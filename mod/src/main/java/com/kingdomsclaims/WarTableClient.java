package com.kingdomsclaims;

import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Visible numerical armies, tactical orders and four selectable skin textures. */
public final class WarTableClient {
    private WarTableClient(){}
    private static final String[] COLORS={"Crimson","Royal Blue","Forest","Sand"};
    public static void accept(WarTableNetwork.Snapshot snapshot){
        Minecraft mc=Minecraft.getInstance();
        if(mc.screen instanceof WarScreen old&&old.data.pos().equals(snapshot.pos())){old.apply(snapshot);return;}
        mc.setScreen(new WarScreen(snapshot));
    }
    private static final class WarScreen extends Screen {
        private WarTableNetwork.Snapshot data;
        private UUID selected;
        private EditBox name,rallyX,rallyZ;
        private int scroll=0;
        WarScreen(WarTableNetwork.Snapshot snapshot){super(Component.literal("War Table"));data=snapshot;}
        private WarTableNetwork.ArmyInfo current(){
            for(var a:data.armies())if(a.id().equals(selected))return a;
            return null;
        }
        void apply(WarTableNetwork.Snapshot snap){
            String n=name==null?"":name.getValue(),x=rallyX==null?"":rallyX.getValue(),z=rallyZ==null?"":rallyZ.getValue();
            data=snap;if(selected!=null&&data.armies().stream().noneMatch(a->a.id().equals(selected)))selected=null;
            rebuild();name.setValue(n);rallyX.setValue(x);rallyZ.setValue(z);
        }
        private void rebuild(){clearWidgets();init();}
        private void btn(String label,int x,int y,int w,int h,String hint,Runnable run,boolean enabled){
            Button b=Button.builder(Component.literal(label),p->run.run()).bounds(x,y,w,h).build();
            b.setTooltip(Tooltip.create(Component.literal(hint)));b.active=enabled;addRenderableWidget(b);
        }
        private void cmd(String action,String value){WarTableNetwork.CHANNEL.sendToServer(new WarTableNetwork.Action(data.pos(),action,value));}
        private void selectedCmd(String action){if(selected!=null)cmd(action,selected.toString());}
        private void selectedCmd(String action,String arg){if(selected!=null)cmd(action,selected+"|"+arg);}
        @Override protected void init(){
            int left=width/2-245,top=height/2-145;
            name=new EditBox(font,left+12,top+42,160,18,Component.literal("New army name"));name.setMaxLength(24);name.setHint(Component.literal("Name army"));addRenderableWidget(name);
            btn("Create",left+177,top+42,60,18,"Create one of 15 possible named armies.",()->cmd("create",name.getValue()),data.authorized());
            btn("Close",left+423,top+42,55,18,"Return to Minecraft",this::onClose,true);
            btn("OPEN TACTICAL MAP",left+279,top+42,137,18,"View discovered terrain and click to order armies to march.",()->WarMapNetwork.CHANNEL.sendToServer(new WarMapNetwork.Request(data.pos(),data.pos().getX()>>4,data.pos().getZ()>>4)),data.authorized());
            for(int i=scroll;i<Math.min(scroll+6,data.armies().size());i++){
                var a=data.armies().get(i);int y=top+81+(i-scroll)*27;
                String text=font.plainSubstrByWidth((a.id().equals(selected)?"> ":"")+a.name()+" ("+a.strength()+" + "+a.fielded()+" field)",218);
                btn(text,left+11,y,226,23,"Click to manage formation, uniform, rally point and troop strength.",()->{selected=a.id();rebuild();},true);
            }
            btn("▲",left+244,top+82,24,21,"Scroll list up",()->{scroll=Math.max(0,scroll-1);rebuild();},scroll>0);
            btn("▼",left+244,top+109,24,21,"Scroll list down",()->{scroll=Math.min(Math.max(0,data.armies().size()-6),scroll+1);rebuild();},scroll+6<data.armies().size());
            var a=current();boolean ok=a!=null&&data.authorized();
            btn("+10",left+279,top+83,53,20,"Assign 10 reserve troops at a nearby, stationary War Table.",()->selectedCmd("assign10"),ok&&data.reserves()>=10);
            btn("+50",left+337,top+83,53,20,"Assign 50 reserve troops at headquarters.",()->selectedCmd("assign50"),ok&&data.reserves()>=50);
            btn("-10",left+395,top+83,39,20,"Return 10 assigned troops to reserve at headquarters.",()->selectedCmd("return10"),ok&&a.strength()>=10);
            btn("-50",left+439,top+83,39,20,"Return 50 assigned troops to reserve at headquarters.",()->selectedCmd("return50"),ok&&a.strength()>=50);
            btn("Formation: "+(a==null?"--":a.formation()),left+279,top+111,199,21,"Cycle Column / Line / Wedge. Applied to deployed soldiers following you.",()->{
                if(a!=null){String next=a.formation().equals("column")?"line":a.formation().equals("line")?"wedge":"column";selectedCmd("formation",next);}},ok);
            btn("Uniform: "+(a==null?"--":COLORS[Math.max(0,Math.min(3,a.uniform()))]),left+279,top+138,199,21,"Choose one of four player-style army skins. Recolor using a resource pack. No armor models required.",()->{if(a!=null)selectedCmd("uniform",String.valueOf((a.uniform()+1)%4));},ok);
            rallyX=new EditBox(font,left+281,top+180,86,18,Component.literal("Rally X"));rallyX.setMaxLength(11);rallyX.setHint(Component.literal("Rally X"));addRenderableWidget(rallyX);
            rallyZ=new EditBox(font,left+374,top+180,86,18,Component.literal("Rally Z"));rallyZ.setMaxLength(11);rallyZ.setHint(Component.literal("Rally Z"));addRenderableWidget(rallyZ);
            btn("March",left+281,top+206,91,21,"Move numerical army to X/Z at 1.5 blocks per second; no instant teleporting.",()->selectedCmd("march",rallyX.getValue()+"|"+rallyZ.getValue()),ok&&a.strength()>0&&a.fielded()==0);
            btn("Hold",left+380,top+206,80,21,"Stop numerical army at current position.",()->selectedCmd("hold"),ok);
            btn("Return HQ",left+281,top+231,91,20,"March back to the original War Table position.",()->selectedCmd("home"),ok&&a.fielded()==0);
            btn("Split 1/2",left+380,top+231,80,20,"Split half the stationary army into a new force. Keeps total manpower unchanged.",()->selectedCmd("split"),ok&&a.strength()>=2&&a.fielded()==0);
            btn("Deploy 1",left+14,top+260,82,21,"Physically deploy one army soldier near this table. Uses assigned troops.",()->selectedCmd("deploy1"),ok&&a.strength()>0);
            btn("Deploy 5",left+101,top+260,82,21,"Physically deploy 5 army soldiers with chosen custom skin. Max 30 local.",()->selectedCmd("deploy5"),ok&&a.strength()>=5);
            btn("Recall",left+188,top+260,82,21,"Return surviving nearby deployed army soldiers. Lost soldiers are not restored.",()->selectedCmd("recall"),ok&&a.fielded()>0);
            btn("Refresh",left+373,top+260,86,21,"Refresh locations, remaining ETA and counts.",()->selectedCmd("status"),ok);
            btn("Disband",left+282,top+260,88,21,"Only completely empty armies can be disbanded.",()->selectedCmd("disband"),ok&&a.strength()==0&&a.fielded()==0);
        }
        @Override public boolean mouseScrolled(double x,double y,double delta){
            if(data.armies().size()>6){scroll=Math.max(0,Math.min(Math.max(0,data.armies().size()-6),scroll+(delta<0?1:-1)));rebuild();return true;}
            return super.mouseScrolled(x,y,delta);
        }
        @Override public void render(GuiGraphics g,int mouseX,int mouseY,float tick){
            renderBackground(g);int left=width/2-245,top=height/2-145;
            g.fill(left,top,left+490,top+294,0xEE20272B);
            g.drawCenteredString(font,"WAR TABLE | "+data.faction()+" ("+data.type()+")",width/2,top+10,0xE6C68E);
            g.drawCenteredString(font,"Reserve "+data.reserves()+" | Assigned "+data.assigned()+" | Armies "+data.armies().size()+"/15",width/2,top+25,0xDDE1E0);
            g.drawString(font,"Armies: click to select",left+12,top+67,0xDCD3C3);
            var a=current();
            if(a!=null){
                g.drawString(font,"Current: "+a.x()+", "+a.z()+"   Troops: "+a.strength()+"+"+a.fielded(),left+279,top+67,0xF4DFA6);
                g.drawString(font,a.marching()?"March to "+a.rallyX()+", "+a.rallyZ()+"  ETA: "+a.secondsRemaining()+"s":"Status: holding position",left+279,top+165,0xC1CFCB);
            }else g.drawString(font,"Select an army above.",left+280,top+67,0xBBBBBB);
            g.drawString(font,"Strategic marching: straight-line route; terrain navigation + battles come later.",left+12,top+246,0xADB4AF);
            if(!data.message().isBlank())g.drawCenteredString(font,font.plainSubstrByWidth(data.message(),478),width/2,top+283,0xF1D28E);
            super.render(g,mouseX,mouseY,tick);
        }
        @Override public boolean isPauseScreen(){return false;}
    }
}
