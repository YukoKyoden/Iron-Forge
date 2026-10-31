package com.kingdomsclaims;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
public final class SupplyDepotClient {
    public static void accept(SupplyDepotNetwork.View p){if(ProductionClient.active())return;var mc=Minecraft.getInstance();if(mc.screen instanceof DepotScreen s)s.data=p;else mc.setScreen(new DepotScreen(p));}
    private static final class DepotScreen extends Screen {
        SupplyDepotNetwork.View data;int selected=0,ticks=0,page=0;
        DepotScreen(SupplyDepotNetwork.View p){super(Component.literal("Kingdom Warehouse"));data=p;}
        void send(String action){selected=Math.min(selected,data.keys().length-1);SupplyDepotNetwork.CHANNEL.sendToServer(new SupplyDepotNetwork.Transfer(data.pos(),action,data.keys()[Math.min(selected,data.keys().length-1)]));}
        @Override protected void init(){int x=width/2-178,y=height/2-105;
            addRenderableWidget(Button.builder(Component.literal("<"),b->{page=Math.max(0,page-1);selected=page*8;}).bounds(x+304,y+22,18,18).build());
            addRenderableWidget(Button.builder(Component.literal(">"),b->{page=Math.min((data.keys().length-1)/8,page+1);selected=page*8;}).bounds(x+326,y+22,18,18).build());
            addRenderableWidget(Button.builder(Component.literal("Deposit stack"),b->send("deposit")).bounds(x+12,y+145,105,21).build());
            addRenderableWidget(Button.builder(Component.literal("Withdraw stack"),b->send("withdraw")).bounds(x+125,y+145,105,21).build());
            addRenderableWidget(Button.builder(Component.literal("Deposit all"),b->send("all")).bounds(x+238,y+145,105,21).build());
            addRenderableWidget(Button.builder(Component.literal("Production"),b->ProductionNetwork.request()).bounds(x+12,y+177,105,20).build());
            addRenderableWidget(Button.builder(Component.literal("Close"),b->onClose()).bounds(x+238,y+177,105,20).build());
        }
        @Override public boolean mouseClicked(double mx,double my,int button){int x=width/2-178,y=height/2-105;for(int i=page*8;i<Math.min(page*8+8,data.keys().length);i++){int xx=x+12+(i%8)*42,yy=y+47;if(mx>=xx&&mx<xx+40&&my>=yy&&my<yy+40){selected=i;if(button==1)send("withdraw");else if(hasShiftDown())send("deposit");return true;}}return super.mouseClicked(mx,my,button);}
        @Override public void tick(){if(++ticks%100==0)send("refresh");}
        @Override public void render(GuiGraphics g,int mx,int my,float pt){renderBackground(g);selected=Math.min(selected,data.keys().length-1);page=Math.min(page,(data.keys().length-1)/8);int x=width/2-178,y=height/2-105;MinecraftMenu.panel(g,x,y,x+356,y+210);g.drawString(font,"KINGDOM SHARED STOCKPILE",x+12,y+12,0xFF404040,false);g.drawString(font,"Storage "+data.used()+" / "+data.capacity(),x+12,y+29,0xFF404040,false);
            for(int i=page*8;i<Math.min(page*8+8,data.keys().length);i++){int xx=x+12+(i%8)*42;if(i==selected)g.fill(xx-1,y+45,xx+41,y+89,0xFFFFFFFF);ResourceSlots.slot(g,xx,y+47,data.keys()[i],data.stored()[i],data.rates()[i],0,mx,my);}
            g.drawString(font,font.plainSubstrByWidth(EconomyService.goodsItem(data.keys()[Math.min(selected,data.keys().length-1)]).getDescription().getString()+"  /  carried: "+data.carried()[selected],330),x+12,y+100,0xFF404040,false);g.drawString(font,"Shift-click: deposit  |  Right-click: withdraw",x+12,y+117,0xFF404040,false);g.drawString(font,font.plainSubstrByWidth(data.message(),330),x+12,y+134,0xFF404040,false);super.render(g,mx,my,pt);
        }
        @Override public boolean isPauseScreen(){return false;}
    }
}
