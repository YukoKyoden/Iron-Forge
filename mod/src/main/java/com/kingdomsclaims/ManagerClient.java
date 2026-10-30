package com.kingdomsclaims;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
public final class ManagerClient {
    public static void accept(ManagerNetwork.View data){var mc=Minecraft.getInstance();if(mc.screen instanceof ManagerScreen s){s.data=data;s.rebuild();}else mc.setScreen(new ManagerScreen(data));}
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
            if(tab==1)btn("Refresh",x+120,y+190,300,()->send("refresh",""));
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
            MinecraftMenu.panel(g,x,y,x+440,y+250);MinecraftMenu.inset(g,x+112,y+30,x+429,y+204);g.drawString(font,"KINGDOM MANAGER  /  "+data.name(),x+10,y+10,0xFF404040,false);
            if(tab==0){g.drawString(font,"Population  "+data.population()+" / "+data.housing(),x+120,y+39,0xFF404040,false);g.drawString(font,"Towns  "+data.towns()+"     Troops  "+data.troops(),x+120,y+59,0xFF404040,false);
                g.drawString(font,data.funded()?"Upkeep in "+data.seconds()+" seconds":"UPKEEP OVERDUE - missing resources",x+120,y+86,data.funded()?0xFF246B26:0xFFA01919,false);
                int[] ids={0,3,7};for(int i=0;i<3;i++){int n=ids[i];ResourceSlots.slot(g,x+120+i*50,y+105,OperationsData.GOODS[n],data.stock()[n],0,data.upkeep()[n],mx,my);g.drawString(font,"-"+data.upkeep()[n],x+126+i*50,y+147,0xFF404040,false);}
            }
            if(tab==1){for(int i=0;i<8;i++)ResourceSlots.slot(g,x+120+(i%4)*52,y+40+(i/4)*55,OperationsData.GOODS[i],data.stock()[i],data.rates()[i],data.upkeep()[i],mx,my);
                g.drawString(font,"Storage  "+data.used()+" / "+data.storage(),x+120,y+158,0xFF404040,false);g.drawString(font,"Deposit / withdraw items at any warehouse core.",x+120,y+174,0xFF404040,false);
            }
            if(tab==2){g.drawString(font,"Registered structures: "+data.buildings().size(),x+120,y+62,0xFF404040,false);int start=page*5;for(int i=start;i<Math.min(start+5,data.buildings().size());i++){var r=data.buildings().get(i);g.drawString(font,font.plainSubstrByWidth(r.type()+"  "+r.capacity()+"  "+r.location(),236),x+120,y+82+(i-start)*24,r.active()?0xFF246B26:0xFFA01919,false);}}
            if(tab==3){g.drawString(font,"Reserve "+data.reserve()+"  /  Total troops "+data.troops(),x+120,y+40,0xFF404040,false);g.drawString(font,"Barracks capacity: "+data.barracks(),x+120,y+59,0xFF404040,false);ResourceSlots.slot(g,x+120,y+76,"IRON",data.stock()[0],0,3,mx,my);ResourceSlots.slot(g,x+168,y+76,"WHEAT",data.stock()[3],0,18,mx,my);g.drawString(font,"Per troop: 3 iron + 18 wheat",x+120,y+142,0xFF404040,false);}
            if(tab==4)g.drawString(font,"Territory, scouting, armies and construction",x+120,y+42,0xFF404040,false);
            g.drawString(font,font.plainSubstrByWidth(data.message(),410),x+12,y+236,0xFF404040,false);super.render(g,mx,my,pt);
        }
        @Override public boolean isPauseScreen(){return false;}
    }
}
