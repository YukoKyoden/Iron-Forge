package com.kingdomsclaims;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
public final class ManagerClient {
    public static void accept(ManagerNetwork.View data){if(ProductionClient.active())return;var mc=Minecraft.getInstance();if(mc.screen instanceof ManagerScreen s){s.data=data;s.rebuild();}else mc.setScreen(new ManagerScreen(data));}
    private static final class ManagerScreen extends Screen {
        private ManagerNetwork.View data;private int tab=0,page=0,ticks=0;private final String[] tabs={"Kingdom","Stockpile","Build","Army","Map"};
        ManagerScreen(ManagerNetwork.View v){super(Component.literal("Kingdom Manager"));data=v;}
        void send(String action,String target){ManagerNetwork.CHANNEL.sendToServer(new ManagerNetwork.Request(action,target));}
        void rebuild(){clearWidgets();init();}
        void btn(String label,int x,int y,int w,Runnable run){addRenderableWidget(Button.builder(Component.literal(label),b->run.run()).bounds(x,y,w,20).build());}
        @Override protected void init(){int x=width/2-220,y=height/2-125;
            for(int i=0;i<5;i++){final int t=i;btn((tab==i?"> ":"")+tabs[i],x+8,y+35+i*26,94,()->{tab=t;page=0;rebuild();});}
            btn("Close",x+8,y+211,94,this::onClose);
            if(tab==0){btn("Ranks & permissions",x+120,y+164,145,()->send("kingdom",""));btn("Towns",x+277,y+164,145,()->send("towns",""));}
            if(tab==1){btn("Where production comes from",x+120,y+211,195,ProductionNetwork::request);btn("Refresh",x+325,y+211,95,()->send("refresh",""));}
            if(tab==2){
                btn("Register a building",x+120,y+35,145,()->{Minecraft.getInstance().setScreen(null);WorldSelectionClient.registerBuilding();});
                btn("Blueprints / 3D planner",x+277,y+35,145,()->send("planner",""));
                int start=page*5;for(int i=start;i<Math.min(start+5,data.buildings().size());i++){var row=data.buildings().get(i);int yy=y+76+(i-start)*24;btn("Remove",x+365,yy,57,()->send("remove",row.id()));}
                btn("Previous",x+120,y+211,90,()->{page=Math.max(0,page-1);rebuild();});btn("Next",x+332,y+211,90,()->{page=Math.min(Math.max(0,(data.buildings().size()-1)/5),page+1);rebuild();});
            }
            if(tab==3){btn("Recruit 1",x+120,y+111,94,()->send("hire1",""));btn("Recruit 10",x+224,y+111,94,()->send("hire10",""));btn("Recruit 50",x+328,y+111,94,()->send("hire50",""));btn("Army command",x+120,y+164,145,()->send("military",""));btn("War map",x+277,y+164,145,()->send("map",""));}
            if(tab==4){btn("Claims & 3D planner",x+120,y+76,300,()->send("planner",""));btn("Kingdom / army map",x+120,y+111,300,()->send("map",""));btn("Town management",x+120,y+146,300,()->send("towns",""));}
        }
        @Override public void tick(){if(++ticks%100==0)send("refresh","");}
        @Override public void render(GuiGraphics g,int mx,int my,float pt){renderBackground(g);int x=width/2-220,y=height/2-125;
            MinecraftMenu.panel(g,x,y,x+440,y+250);MinecraftMenu.inset(g,x+112,y+30,x+429,y+204);g.fill(x+113,y+31,x+428,y+203,0xFFC6C6C6);g.drawString(font,"KINGDOM MANAGER  /  "+data.name(),x+10,y+10,0xFF404040,false);
            if(tab==0){g.drawString(font,"Population  "+data.population()+" / "+data.housing(),x+120,y+39,0xFF404040,false);g.drawString(font,"Towns  "+data.towns()+"     Troops  "+data.troops(),x+120,y+59,0xFF404040,false);
                g.drawString(font,data.funded()?"Next upkeep payment in "+ProductionText.time(data.seconds()):"UPKEEP OVERDUE - missing resources",x+120,y+86,data.funded()?0xFF246B26:0xFFA01919,false);
                g.drawString(font,"COST EVERY 5 MINUTES",x+120,y+105,0xFF404040,false);
                g.drawString(font,ProductionText.count(data.upkeep()[0])+" iron + "+ProductionText.count(data.upkeep()[3])+" wheat",x+120,y+121,0xFF404040,false);
                g.drawString(font,ProductionText.count(data.upkeep()[7])+" gold nuggets",x+120,y+137,0xFF404040,false);
            }
            if(tab==1){
                g.drawString(font,"Resource",x+120,y+38,0xFF404040,false);g.drawString(font,"Stored",x+233,y+38,0xFF404040,false);g.drawString(font,"Expected gain",x+304,y+38,0xFF404040,false);
                int[] order={0,2,3,1,4,5,6,7};
                for(int row=0;row<8;row++){int i=order[row],yy=y+54+row*17;
                    g.renderItem(new net.minecraft.world.item.ItemStack(EconomyService.goodsItem(OperationsData.GOODS[i])),x+119,yy-3);
                    g.drawString(font,ProductionText.NAMES[i],x+138,yy+1,0xFF404040,false);
                    g.drawString(font,ProductionText.count(data.stock()[i]),x+233,yy+1,0xFF404040,false);
                    String rate=i==7?"+"+ProductionText.count(data.taxes())+" / 5 min":"+"+ProductionText.quantity(data.rates()[i])+" / 6 min";
                    if(data.used()>=data.storage())rate="Storage full";
                    g.drawString(font,rate,x+304,yy+1,data.used()>=data.storage()?0xFFA01919:0xFF246B26,false);
                    if(mx>=x+115&&mx<x+425&&my>=yy-3&&my<yy+14)g.renderComponentTooltip(font,java.util.List.of(Component.literal(ProductionText.NAMES[i]+" stored: "+ProductionText.count(data.stock()[i])),Component.literal(i==7?"Tax income: +"+ProductionText.count(data.taxes())+" every 5 minutes":ProductionText.rate(data.rates()[i],360)),Component.literal("Expected output from currently running producers; click below for source timers.")),mx,my);
                }
                g.drawString(font,"Storage: "+ProductionText.count(data.used())+" / "+ProductionText.count(data.storage()),x+120,y+194,0xFF404040,false);
            }
            if(tab==2){g.drawString(font,"Registered structures: "+data.buildings().size(),x+120,y+62,0xFF404040,false);int start=page*5;for(int i=start;i<Math.min(start+5,data.buildings().size());i++){var r=data.buildings().get(i);g.drawString(font,font.plainSubstrByWidth(ProductionText.building(r.type(),r.capacity(),r.active()),236),x+120,y+82+(i-start)*24,r.active()?0xFF246B26:0xFFA01919,false);}}
            if(tab==3){g.drawString(font,"Reserve "+data.reserve()+"  /  Total troops "+data.troops(),x+120,y+40,0xFF404040,false);g.drawString(font,"Barracks capacity: "+data.barracks(),x+120,y+59,0xFF404040,false);ResourceSlots.slot(g,x+120,y+76,"IRON",data.stock()[0],0,3,mx,my);ResourceSlots.slot(g,x+168,y+76,"WHEAT",data.stock()[3],0,18,mx,my);g.drawString(font,"Per troop: 3 iron + 18 wheat",x+120,y+142,0xFF404040,false);}
            if(tab==4)g.drawString(font,"Territory, scouting, armies and construction",x+120,y+42,0xFF404040,false);
            g.drawString(font,font.plainSubstrByWidth(data.message(),410),x+12,y+236,0xFF404040,false);super.render(g,mx,my,pt);
        }
        @Override public boolean isPauseScreen(){return false;}
    }
}
