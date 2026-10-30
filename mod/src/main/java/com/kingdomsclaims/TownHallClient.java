package com.kingdomsclaims;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Town Hall / Kingdom management client. All mutations are verified again on the server. */
@Mod.EventBusSubscriber(modid=KingdomsClaims.MODID,value=Dist.CLIENT)
public final class TownHallClient {
    private TownHallClient(){}
    private static final KeyMapping KEY=new KeyMapping("key.kingdomsclaims.townhall",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_H,"key.categories.kingdomsclaims");
    private static HallScreen current;
    @Mod.EventBusSubscriber(modid=KingdomsClaims.MODID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class Register {
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event){event.register(KEY);}
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase==TickEvent.Phase.END)while(KEY.consumeClick()){
            if(Minecraft.getInstance().player!=null)TownHallNetwork.CHANNEL.sendToServer(new TownHallNetwork.Open());
        }
    }
    public static void accept(TownHallNetwork.Snapshot state){
        Minecraft mc=Minecraft.getInstance();
        if(state.open()||!(mc.screen instanceof HallScreen)){
            current=new HallScreen(state);mc.setScreen(current);
        }else if(current!=null&&mc.screen==current){current.update(state);}
    }

    private static final class HallScreen extends Screen {
        private TownHallNetwork.Snapshot snapshot;
        private int tab,memberIndex=0,townOffset=0,memberOffset=0,onlineOffset=0;
        private boolean townDirectory=false,onlineBrowse=false;
        private String pendingAction="",pendingId="",pendingDescription="";
        private EditBox input;private String note="";
        private final String[] tabs={"Overview","Claims","Members","Perms","Banner","Kingdom"};
        private static final int TOWN_ROWS=7,MEMBER_ROWS=5;
        private HallScreen(TownHallNetwork.Snapshot s){
            super(Component.literal("Kingdoms & Claims"));snapshot=s;note=s.message();tab=s.kingdomOpen()?5:0;
        }
        private int w(){return Math.min(476,width-12);}
        private int h(){return Math.min(294,height-12);}
        private int x(){return (width-w())/2;}
        private int y(){return (height-h())/2;}
        private static int clamp(int n,int max){return Math.max(0,Math.min(n,Math.max(0,max)));}
        private TownHallNetwork.TownInfo active(){
            for(var t:snapshot.towns())if(t.id().equals(snapshot.selectedId()))return t;
            return null;
        }
        private TownHallNetwork.Member picked(){
            if(snapshot.members().isEmpty())return null;
            memberIndex=clamp(memberIndex,snapshot.members().size()-1);
            return snapshot.members().get(memberIndex);
        }
        private void action(String type){send(type,"",0,false);}
        private void send(String type,String text,int number,boolean yes){TownHallNetwork.CHANNEL.sendToServer(new TownHallNetwork.Action(type,text,number,yes));}
        private Button add(String text,int px,int py,int pw,int ph,Runnable callback){
            return addRenderableWidget(Button.builder(Component.literal(text),b->callback.run()).bounds(px,py,pw,ph).build());
        }
        private Button addTip(String text,int px,int py,int pw,int ph,String help,Runnable callback){
            Button b=add(text,px,py,pw,ph,callback);
            b.setTooltip(Tooltip.create(Component.literal(help)));
            return b;
        }
        private void changeTab(int to){tab=to;townDirectory=false;clearPending();onlineBrowse=false;rebuild();}
        private void clearPending(){pendingAction="";pendingId="";pendingDescription="";}
        private void confirm(String type,String value,String description){
            pendingAction=type;pendingId=value;pendingDescription=description;
            rebuild();
        }
        private void rebuild(){clearWidgets();init();}
        private void update(TownHallNetwork.Snapshot s){
            String previousMember=picked()==null?"":picked().id();
            snapshot=s;note=s.message();clearPending();
            for(int i=0;i<s.members().size();i++)if(s.members().get(i).id().equals(previousMember)){memberIndex=i;break;}
            memberIndex=clamp(memberIndex,s.members().size()-1);
            townOffset=clamp(townOffset,s.towns().size()-TOWN_ROWS);
            memberOffset=clamp(memberOffset,s.members().size()-MEMBER_ROWS);
            onlineOffset=clamp(onlineOffset,s.onlinePlayers().size()-MEMBER_ROWS);
            rebuild();
        }
        private void switchDirectory(){townDirectory=!townDirectory;clearPending();rebuild();}
        private void scrollTowns(int difference){townOffset=clamp(townOffset+difference,snapshot.towns().size()-TOWN_ROWS);rebuild();}
        private int listLength(){return onlineBrowse?snapshot.onlinePlayers().size():snapshot.members().size();}
        private int listOffset(){return onlineBrowse?onlineOffset:memberOffset;}
        private void scrollMembers(int difference){
            if(onlineBrowse)onlineOffset=clamp(onlineOffset+difference,listLength()-MEMBER_ROWS);
            else memberOffset=clamp(memberOffset+difference,listLength()-MEMBER_ROWS);
            rebuild();
        }
        private void memberRow(int index){memberIndex=index;rebuild();}
        private EditBox field(int px,int py,int pw,String hint){
            EditBox box=new EditBox(font,px,py,pw,20,Component.literal(hint));box.setMaxLength(24);box.setHint(Component.literal(hint));
            addRenderableWidget(box);return box;
        }
        @Override protected void init(){
            int left=x(),top=y(),wide=w(),content=top+84;
            int bottom=top+h()-49;
            addTip(townDirectory?"Back to menu":"All towns ("+snapshot.towns().size()+")",left+10,top+27,132,20,"Show all of your towns in a scrollable directory.",this::switchDirectory);
            add("Close",left+wide-70,top+27,60,20,this::onClose);
            if(!pendingAction.isEmpty()){
                addTip("Confirm",left+wide/2-130,top+180,122,25,"Proceed with the action shown above.",()->{
                    String action=pendingAction,id=pendingId;clearPending();send(action,id,0,false);
                });
                addTip("Cancel",left+wide/2+8,top+180,122,25,"Return without changing anything.",()->{clearPending();rebuild();});
                return;
            }
            if(townDirectory){
                add("▲",left+wide-46,content+1,32,20,()->scrollTowns(-1));
                add("▼",left+wide-46,content+132,32,20,()->scrollTowns(1));
                for(int i=townOffset;i<Math.min(snapshot.towns().size(),townOffset+TOWN_ROWS);i++){
                    var town=snapshot.towns().get(i);
                    String text=(town.id().equals(snapshot.selectedId())?"✓  ":"    ")+town.name()+"  "+(town.mayor()?"(Mayor)":"(Member)");
                    add(text,left+14,content+3+(i-townOffset)*22,wide-74,20,()->{
                        townDirectory=false;send("select",town.id(),0,false);
                    });
                }
                return;
            }
            int tabLeft=left+10,tabWidth=(wide-20)/tabs.length;
            for(int i=0;i<tabs.length;i++){
                int idx=i;
                Button b=add(tabs[i],tabLeft+i*tabWidth,top+54,tabWidth-2,20,()->changeTab(idx));
                b.active=i!=tab;
            }
            TownHallNetwork.TownInfo town=active();
            switch(tab){
                case 0->{
                    input=field(left+12,bottom,wide-132,"New town name");
                    add("Create town",left+wide-116,bottom,104,20,()->send("create",input.getValue().trim(),0,false));
                    if(town==null)add("Accept Invitation",left+12,content+30,130,20,()->action("accept"));
                    else {
                        addTip("Claim view (K)",left+12,content+76,125,20,"Preview territory and prices before buying a chunk.",()->TerritoryClient.openTownSurvey());
                        add("Accept Invitation",left+144,content+76,132,20,()->action("accept"));
                    }
                }
                case 1->{
                    addTip("Open claim map",left+12,content+55,140,22,"View neighboring chunks, boundaries, and claim eligibility.",()->TerritoryClient.openTownSurvey());
                    addTip("Claim standing chunk",left+163,content+55,157,22,"Costs 16 cobblestone and 20 iron. Must be connected and inside 9x9.",()->action("claim"));
                }
                case 2,3->{
                    // The two people tabs share a scrollable member list; Members also lets you browse online players.
                    if(tab==2){
                        input=field(left+12,bottom,wide-132,"Player name or select online");
                        add("Invite player",left+wide-116,bottom,104,20,()->send("invite",input.getValue().trim(),0,false));
                        add(onlineBrowse?"Show members":"Browse online",left+12,content+3,142,20,()->{onlineBrowse=!onlineBrowse;rebuild();});
                    }
                    int listY=content+(tab==2?30:24);
                    add("▲",left+168,listY,24,20,()->scrollMembers(-1));
                    add("▼",left+168,listY+96,24,20,()->scrollMembers(1));
                    int offset=listOffset();
                    for(int i=offset;i<Math.min(listLength(),offset+MEMBER_ROWS);i++){
                        final int idx=i;
                        if(onlineBrowse&&tab==2){
                            String player=snapshot.onlinePlayers().get(i);
                            boolean member=snapshot.members().stream().anyMatch(m->m.name().equals(player));
                            add(player+(member?" ✓":""),left+12,listY+(i-offset)*23,153,21,()->{
                                if(input!=null)input.setValue(player);
                            });
                        }else{
                            var member=snapshot.members().get(i);
                            add((i==memberIndex?"▶ ":"  ")+member.name(),left+12,listY+(i-offset)*23,153,21,()->memberRow(idx));
                        }
                    }
                    var member=picked();
                    if(tab==2){
                        if(member!=null){
                            addTip("Remove member",left+202,content+62,139,21,"Remove this member's town access.",()->confirm("kick",member.id(),"Remove "+member.name()+" from this town?"));
                            addTip("Make Mayor",left+202,content+88,139,21,"This permanently transfers town leadership to this member.",()->confirm("transfer",member.id(),"Transfer mayorship to "+member.name()+"?"));
                        }
                        addTip("Leave town",left+202,content+114,139,21,"Leave this town. Mayors must transfer leadership first.",()->confirm("leave","","Leave your selected town?"));
                    }else if(member!=null){
                        int[] masks={1,2,4,8,16,32};String[] labels={"Build","Break","Storage","Claim","Banner","Gates"};
                        for(int i=0;i<masks.length;i++){
                            int bit=masks[i],cx=i%2,cy=i/2;boolean allowed=(member.grants()&bit)!=0;
                            Button toggle=addTip(labels[i]+": "+(allowed?"ON":"OFF"),left+202+cx*119,content+50+cy*26,114,22,
                                "Allow or deny "+labels[i].toLowerCase(Locale.ROOT)+" actions for "+member.name()+".",
                                ()->send("permit",member.id(),bit,!allowed));
                            toggle.active=active()!=null&&active().mayor()&&member.rank()!=3;
                        }
                    }
                }
                case 4->{
                    input=field(left+12,bottom,wide-132,"New town name");
                    addTip("Rename town",left+wide-116,bottom,104,20,"Changes this town's display name without losing territory.",()->send("rename",input.getValue().trim(),0,false));
                    addTip("Relocate banner",left+12,content+45,147,20,"Look at the new placement block; costs 8 iron and has cooldown.",()->confirm("relocate","","Move the Town Banner to the block you're targeting? Costs 8 iron."));
                    if(snapshot.ownsBoth()){
                        Button merge=addTip("Merge into Kingdom",left+166,content+45,150,20,
                            snapshot.canMergeOwn()?"Your town and kingdom share an edge. Cost: "+snapshot.ownMergeCost()+" iron from the kingdom treasury.":
                                "Your town and kingdom must share a chunk edge before they can merge.",
                            ()->confirm("mergeOwn","","Merge this town into your kingdom for "+snapshot.ownMergeCost()+" iron from the kingdom treasury?"));
                        merge.active=snapshot.canMergeOwn();
                    }else{
                        Button approve=addTip("Approve annexation",left+166,content+45,150,20,
                            "Approve annexation by the adjacent kingdom. Their King must then complete the purchase.",
                            ()->{if(!snapshot.nearbyKingdom().isEmpty())send("approve",snapshot.nearbyKingdom(),0,false);});
                        approve.active=!snapshot.nearbyKingdom().isEmpty();
                    }
                    addTip("Disband town",left+12,content+93,190,22,"Permanently deletes this town and releases its claims.",
                        ()->confirm("disband","","PERMANENT: Disband town and release all of its claims?"));
                }
                case 5->{
                    add("Open kingdom claim map",left+12,content+85,180,23,()->TerritoryClient.openKingdomSurvey());
                    Button b=addTip("Relocate Kingdom Banner",left+202,content+85,205,23,
                        "Look at the new site inside your kingdom, 8 iron, 30-minute cooldown.",
                        ()->confirm("kingdomRelocate","","Move your only capital banner? Costs 8 iron."));
                    b.active=snapshot.kingdom().canRelocate();
                }
            }
        }
        @Override public boolean mouseScrolled(double mouseX,double mouseY,double delta){
            int left=x(),top=y();
            if(townDirectory&&mouseY>=top+84&&mouseY<=top+h()-25){scrollTowns(delta>0?-1:1);return true;}
            if((tab==2||tab==3)&&mouseX>=left+12&&mouseX<=left+195&&mouseY>=top+105&&mouseY<=top+250){
                scrollMembers(delta>0?-1:1);return true;
            }
            return super.mouseScrolled(mouseX,mouseY,delta);
        }
        @Override public void render(GuiGraphics g,int mx,int my,float tick){
            renderBackground(g);
            int left=x(),top=y(),wide=w(),high=h();
            g.fill(left,top,left+wide,top+high,0xEE222B26);
            g.renderOutline(left,top,wide,high,0xFF82987A);
            g.drawString(font,"KINGDOMS & CLAIMS",left+11,top+10,0xFFE9D9AA,false);
            if(!pendingAction.isEmpty()){
                g.fill(left+8,top+80,left+wide-8,top+166,0xFF443B30);
                g.renderOutline(left+8,top+80,wide-16,86,0xFFDFB67C);
                g.drawString(font,"CONFIRM ACTION",left+20,top+94,0xFFFFD68B,false);
                g.drawWordWrap(font,Component.literal(pendingDescription),left+20,top+117,wide-40,0xFFEDE7D9);
                g.drawString(font,"You can cancel safely.",left+20,top+151,0xFFCCD3C6,false);
            }else if(townDirectory){
                g.drawString(font,"Your towns · click to select · scroll to see more",left+13,top+77,0xFFE7E9DE,false);
                g.drawString(font,Math.min(snapshot.towns().size(),townOffset+TOWN_ROWS)+" / "+snapshot.towns().size(),left+wide-64,top+242,0xFFD4D5CA,false);
                if(snapshot.towns().isEmpty())g.drawString(font,"No towns yet. Create one from Overview.",left+13,top+112,0xFFE7E9DE,false);
            }else{
                TownHallNetwork.TownInfo t=active();
                String name=tab==5?
                    (snapshot.kingdom().name().isEmpty()?"No kingdom":snapshot.kingdom().name()+" | "+snapshot.kingdom().chunks()+" chunks | "+snapshot.kingdom().treasury()+" iron"):
                    (t==null?"No town selected":t.name()+" | "+t.chunks()+"/81 chunks | "+t.members()+" members");
                if(name.length()>53)name=name.substring(0,50)+"...";
                g.drawString(font,name,left+152,top+33,0xFFE7E9DE,false);
                int cy=top+86;
                switch(tab){
                    case 0->{
                        if(t==null)line(g,"Create a town below, or accept an invitation.",left+13,cy,0xFFE0E0D6);
                        else{
                            line(g,"Mayor: "+(t.mayor()?"You":"Another player")+"    Members: "+t.members(),left+13,cy,0xFFE0E0D6);
                            line(g,"Territory: "+t.chunks()+" / 81 chunks",left+13,cy+16,0xFFE0E0D6);
                            line(g,"Allegiance: "+t.kingdom(),left+13,cy+32,0xFF9CC7D9);
                            line(g,"Town claims: 16 cobblestone + 20 iron per chunk",left+13,cy+48,0xFFDCCB95);
                        }
                    }
                    case 1->{
                        line(g,t==null?"Create or join a town first.":t.name()+": "+t.chunks()+"/81 chunks",left+13,cy,0xFFFFFFFF);
                        line(g,"Claim adjacent chunks within the town's 9x9 boundary.",left+13,cy+19,0xFFD0DACB);
                        line(g,"Cost: 16 cobblestone + 20 iron per chunk.",left+13,cy+39,0xFFE9D2A0);
                    }
                    case 2,3->{
                        var m=picked();
                        if(tab==2){
                            line(g,onlineBrowse?"Online players (click to fill invitation)":"Town members (click to manage)",left+13,cy,0xFFE8D8A9);
                        }else{
                            line(g,"Select a member to edit permissions",left+13,cy,0xFFE0E0D6);
                        }
                        if(m!=null)line(g,"Selected: "+m.name()+(m.rank()==3?" (Mayor)":""),left+202,cy+25,0xFFE2CDA2);
                        else line(g,"No members available.",left+202,cy+25,0xFFE2CDA2);
                        line(g,(listOffset()+Math.min(MEMBER_ROWS,listLength()-listOffset()))+" / "+listLength(),left+13,cy+166,0xFFC5D4C6);
                    }
                    case 4->{
                        line(g,"Banner: "+snapshot.banner(),left+13,cy,0xFFE0E0D6);
                        line(g,"Look at the top of the new banner location.",left+13,cy+22,0xFFCCDECE);
                        line(g,"Cost: 8 iron. No active siege. 30-minute cooldown.",left+13,cy+35,0xFFCCDECE);
                        if(snapshot.ownsBoth()){
                            line(g,snapshot.canMergeOwn()?"Your kingdom: one-click merger · "+snapshot.ownMergeCost()+" iron":"Your kingdom must share a chunk edge to merge.",left+13,cy+71,0xFFF1CA8F);
                        }else{
                            line(g,snapshot.nearbyKingdom().isEmpty()?"No adjacent kingdom available for annexation.":"Adjacent kingdom: "+snapshot.nearbyKingdom(),left+13,cy+71,0xFFF1CA8F);
                        }
                        line(g,"Permanent actions always require confirmation.",left+13,cy+123,0xFFFFC4AD);
                    }
                    case 5->{
                        var k=snapshot.kingdom();
                        line(g,k.name().isEmpty()?"You are not in a kingdom.":"Kingdom: "+k.name()+(k.king()?" (King)":""),left+13,cy,0xFFE0E0D6);
                        line(g,"Territory: "+k.chunks()+" chunks    Treasury: "+k.treasury()+" iron",left+13,cy+16,0xFFD2DCB6);
                        line(g,"Capital banner: "+k.banner(),left+13,cy+32,0xFFE4CE9E);
                        line(g,"Look at new site: inside owned territory, clear 3x3, open sky.",left+13,cy+52,0xFFC5D4C6);
                        line(g,"8 iron · 30-minute cooldown · unavailable during siege",left+13,cy+65,0xFFC5D4C6);
                        if(!k.canRelocate())line(g,"Banner permission required to relocate.",left+13,cy+115,0xFFF1AE9E);
                    }
                }
            }
            if(!note.isEmpty()){
                String message=note.length()>77?note.substring(0,74)+"...":note;
                g.drawString(font,message,left+12,top+high-19,0xFFA9E6B8,false);
            }
            super.render(g,mx,my,tick);
        }
        private void line(GuiGraphics g,String s,int px,int py,int color){g.drawString(font,s,px,py,color,false);}
        @Override public boolean keyPressed(int keyCode,int scanCode,int modifiers){
            if(keyCode==GLFW.GLFW_KEY_ESCAPE&&!pendingAction.isEmpty()){
                clearPending();rebuild();return true;
            }
            return super.keyPressed(keyCode,scanCode,modifiers);
        }
        @Override public boolean isPauseScreen(){return false;}
        @Override public void onClose(){current=null;super.onClose();}
    }
}
