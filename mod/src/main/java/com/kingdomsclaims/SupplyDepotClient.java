package com.kingdomsclaims;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;

/** No custom block entity renderer: lightweight, responsive GUI with fill bars. */
public final class SupplyDepotClient {
    private SupplyDepotClient(){}
    private static DepotScreen open;
    public static void accept(SupplyDepotNetwork.View packet){
        Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;
        if(mc.screen instanceof DepotScreen screen && screen.pos.equals(packet.pos())){screen.update(packet);return;}
        open=new DepotScreen(packet);mc.setScreen(open);
    }
    private static final class DepotScreen extends Screen {
        private final BlockPos pos;
        private SupplyDepotNetwork.View data;
        private int selected=0;
        DepotScreen(SupplyDepotNetwork.View packet){super(Component.literal("Warehouse Core"));pos=packet.pos();data=packet;}
        private void update(SupplyDepotNetwork.View packet){data=packet;}
        private void send(String action){SupplyDepotNetwork.CHANNEL.sendToServer(new SupplyDepotNetwork.Transfer(pos,action,selected));}
        @Override protected void init(){
            int x=width/2-225,y=height/2-174;
            for(int i=0;i<OperationsData.GOODS.length;i++){
                final int choice=i;
                addRenderableWidget(Button.builder(Component.literal(OperationsData.GOODS[i]),b->{selected=choice;})
                    .bounds(x+8,y+40+i*26,94,21).build());
            }
            addRenderableWidget(Button.builder(Component.literal("Store 64"),b->send("deposit")).bounds(x+309,y+52,130,22).build());
            addRenderableWidget(Button.builder(Component.literal("Carry 64"),b->send("withdraw")).bounds(x+309,y+87,130,22).build());
            addRenderableWidget(Button.builder(Component.literal("Store all carried"),b->send("all")).bounds(x+309,y+122,130,22).build());
            addRenderableWidget(Button.builder(Component.literal("Close"),b->onClose()).bounds(x+309,y+256,130,22).build());
        }
        @Override public void render(GuiGraphics g,int mx,int my,float pt){
            renderBackground(g);
            int x=width/2-225,y=height/2-174;
            g.fill(x,y,x+450,y+348,0xF01A2930);
            g.fill(x,y,x+450,y+3,0xFFE8C57C);
            g.drawString(font,"WAREHOUSE CORE · MODULAR STORAGE",x+10,y+9,0xFFE8C57C,false);
            for(int i=0;i<OperationsData.GOODS.length;i++){
                int yy=y+40+i*26,stored=Math.max(0,data.stored()[i]);
                int total=Math.max(1,data.capacity());
                int fill=(int)(Math.min(1.0,stored/(double)total)*164);
                g.fill(x+109,yy,x+287,yy+4,0xFF40524A);
                g.fill(x+109,yy,x+109+fill,yy+4,stored>=total?0xFFE0B36B:0xFF8BC8A0);
                g.drawString(font,(selected==i?"> ":"")+stored+" here | "+data.carried()[i]+" carried",x+111,yy+8,
                    selected==i?0xFFFFE2A8:0xFFCCD8CE,false);
            }
            g.drawString(font,"Shelves: "+data.modules()+"/100     Filled: "+data.used()+"/"+data.capacity()+" items",x+9,y+27,0xFFBAD8CC,false);
            int rate=data.rates()[selected];
            g.drawString(font,"Production: +"+rate+" "+OperationsData.GOODS[selected]+"/min (staffed outposts)",x+108,y+268,rate>0?0xFF9EE2AB:0xFFCCA97D,false);
            if(data.used()>=data.capacity())g.drawString(font,"WAREHOUSE FULL: production waiting for free shelves",x+8,y+296,0xFFFFAC8B,false);
            else if(rate==0)g.drawString(font,"No staffed active production site in range",x+8,y+296,0xFFB8B8A4,false);
            g.drawString(font,"Each shelf links within 10 blocks; max 50 from core.",x+8,y+311,0xFFAFC3B6,false);
            // Resource quantities are stored at this core; static expansion models show fill levels.
            if(!data.message().isBlank())g.drawCenteredString(font,font.plainSubstrByWidth(data.message(),420),width/2,y+333,0xFFFFD898);
            super.render(g,mx,my,pt);
        }
        @Override public boolean isPauseScreen(){return false;}
    }
}
