package com.kingdomsclaims;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Numeric manpower first; spawning physical units is a separate deliberate deployment. */
public final class BarracksClient {
    private BarracksClient(){}
    public static void accept(BarracksNetwork.Snapshot data){
        Minecraft minecraft=Minecraft.getInstance();
        if(minecraft.screen instanceof BarracksScreen old&&old.data.home().equals(data.home())){old.apply(data);return;}
        minecraft.setScreen(new BarracksScreen(data));
    }
    private static final class BarracksScreen extends Screen {
        private BarracksNetwork.Snapshot data;
        BarracksScreen(BarracksNetwork.Snapshot snapshot){super(Component.literal("Barracks"));data=snapshot;}
        void apply(BarracksNetwork.Snapshot s){data=s;clearWidgets();init();}
        private void button(String label,int x,int y,int w,int h,String tooltip,String order,boolean active){
            Button b=Button.builder(Component.literal(label),p->BarracksNetwork.CHANNEL.sendToServer(new BarracksNetwork.Action(data.home(),order))).bounds(x,y,w,h).build();
            b.active=data.authorized()&&active;b.setTooltip(Tooltip.create(Component.literal(tooltip)));addRenderableWidget(b);
        }
        @Override protected void init(){
            int x=width/2-188,y=height/2-108;
            button("Hire 1",x+12,y+74,112,22,"3 iron ingots + 6 bread. Increases saved numerical reserve, no NPC spawned.","hire1",data.reserve()+data.assigned()+1<=data.maxReserve());
            button("Hire 10",x+132,y+74,112,22,"30 iron ingots + 60 bread. Does not spawn NPCs.","hire10",data.reserve()+data.assigned()+10<=data.maxReserve());
            button("Hire 50",x+252,y+74,112,22,"150 iron ingots + 300 bread. Does not spawn NPCs.","hire50",data.reserve()+data.assigned()+50<=data.maxReserve());
            button("Deploy 1",x+12,y+104,172,22,"Consume 1 numerical troop to spawn 1 physical player-shaped unit.","deploy1",data.reserve()>=1&&data.active()<data.maxPhysical());
            button("Deploy 5",x+192,y+104,172,22,"Consume 5 numerical troops to spawn 5 physical units.","deploy5",data.reserve()>=5&&data.active()+5<=data.maxPhysical());
            button("Follow me",x+12,y+134,172,22,"Fielded NPCs walk behind you. No teleporting.","follow",data.active()>0);
            button("Guard here",x+192,y+134,172,22,"Fielded NPCs defend this barracks.","guard",data.active()>0);
            button("Return survivors",x+12,y+164,172,22,"Recall living deployed soldiers nearby back into reserve. Legacy v0.3 guards cannot be converted.","recall",data.active()>0);
            addRenderableWidget(Button.builder(Component.literal("Close"),b->onClose()).bounds(x+192,y+164,172,22).build());
        }
        @Override public void render(GuiGraphics g,int mouseX,int mouseY,float tick){
            renderBackground(g);int x=width/2-188,y=height/2-108;
            g.fill(x,y,x+376,y+215,0xEF242A2B);
            g.drawCenteredString(font,(data.type().equals("town")?"TOWN MILITIA BARRACKS":"KINGDOM SOLDIER BARRACKS"),width/2,y+10,0xE7CD94);
            g.drawCenteredString(font,data.faction(),width/2,y+26,0xE8E8E8);
            g.drawCenteredString(font,"Reserve: "+data.reserve()+"  |  In armies: "+data.assigned()+"  |  Capacity: "+data.maxReserve(),width/2,y+44,0xC6D2C0);
            g.drawCenteredString(font,"NPCs near barracks: "+data.active()+" / "+data.maxPhysical(),width/2,y+57,0xB9BECA);
            if(!data.message().isBlank())g.drawCenteredString(font,font.plainSubstrByWidth(data.message(),361),width/2,y+194,0xE6CB83);
            super.render(g,mouseX,mouseY,tick);
        }
        @Override public boolean isPauseScreen(){return false;}
    }
}
