package com.kingdomsclaims;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
/** Production quantities are visible rows, rather than indicators hidden behind item tooltips. */
public final class ProductionClient {
    private static Screen returnTo;private static boolean pendingOpen=false;
    public static boolean active(){return pendingOpen||Minecraft.getInstance().screen instanceof ProductionScreen;}
    public static void expect(){if(!(Minecraft.getInstance().screen instanceof ProductionScreen))pendingOpen=true;}
    public static void accept(ProductionNetwork.View data){var mc=Minecraft.getInstance();if(mc.screen instanceof ProductionScreen s)s.data=data;else if(pendingOpen){pendingOpen=false;returnTo=mc.screen;mc.setScreen(new ProductionScreen(data));}}
    private static final String[] NAMES={"Iron","Coal","Wood","Wheat","Stone","Emeralds","Gold ingots","Gold nuggets"};
    private static final int[] ORDER={2,4,0,3,1,6,7,5};
    private static final class ProductionScreen extends Screen {
        private ProductionNetwork.View data;private int selected=-1,page=0,ticks=0;
        ProductionScreen(ProductionNetwork.View data){super(Component.literal("Kingdom Production"));this.data=data;}
        int w(){return Math.min(420,width-12);}int h(){return Math.min(300,height-12);}int x(){return (width-w())/2;}int y(){return (height-h())/2;}int visible(){return Math.max(1,(h()-108)/29);}
        List<ProductionNetwork.Source> sources(){return data.sources().stream().filter(s->selected>=0&&(s.mask()&(1<<selected))!=0).toList();}
        void rebuild(){clearWidgets();init();}
        void button(String name,int x,int y,int w,Runnable r){addRenderableWidget(Button.builder(Component.literal(name),b->r.run()).bounds(x,y,w,20).build());}
        @Override protected void init(){int x=x(),y=y()+h()-28,w=w();button("<",x+10,y,30,()->{page=Math.max(0,page-1);});button(">",x+44,y,30,()->{int total=selected<0?8:sources().size();page=Math.min(Math.max(0,(total-1)/visible()),page+1);});button(selected<0?"Close":"All resources",x+w/2-53,y,106,()->{if(selected<0)onClose();else{selected=-1;page=0;rebuild();}});button("Refresh",x+w-84,y,74,ProductionNetwork::request);}
        @Override public void tick(){if(++ticks%40==0)ProductionNetwork.request();}
        @Override public void onClose(){pendingOpen=false;Minecraft.getInstance().setScreen(returnTo);}
        @Override public boolean isPauseScreen(){return false;}
        @Override public boolean mouseClicked(double mx,double my,int button){if(super.mouseClicked(mx,my,button))return true;if(selected<0&&mx>=x()+8&&mx<x()+w()-8&&my>=y()+61&&my<y()+61+visible()*29){int row=(int)(my-y()-61)/29+page*visible();if(row<8){selected=ORDER[row];page=0;rebuild();return true;}}return false;}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){renderBackground(g);int x=x(),y=y(),w=w(),h=h();MinecraftMenu.panel(g,x,y,x+w,y+h);
            g.drawString(font,selected<0?"KINGDOM PRODUCTION":NAMES[selected].toUpperCase()+" PRODUCERS",x+12,y+10,0xFF404040,false);
            g.drawString(font,font.plainSubstrByWidth(selected<0?data.name()+" / actual stockpile deliveries": "Stored: "+data.stock()[selected]+" / Last 60s: +"+data.actual()[selected],w-24),x+12,y+26,0xFF404040,false);
            if(selected<0){int actualX=x+(int)(w*.48),maxX=x+(int)(w*.66),dueX=x+(int)(w*.84);g.drawString(font,"Resource",x+12,y+47,0xFF404040,false);g.drawString(font,"Last 60s",actualX,y+47,0xFF404040,false);g.drawString(font,"Max/6m",maxX,y+47,0xFF404040,false);g.drawString(font,"Upkeep",dueX,y+47,0xFF404040,false);
                for(int row=0;row<visible()&&page*visible()+row<8;row++){int index=ORDER[page*visible()+row],yy=y+61+row*29;MinecraftMenu.inset(g,x+8,yy,x+w-8,yy+26);g.renderItem(new ItemStack(EconomyService.goodsItem(OperationsData.GOODS[index])),x+12,yy+4);g.drawString(font,NAMES[index],x+34,yy+8,0xFFFFFFFF,true);g.drawString(font,"+"+ResourceSlots.compact(data.actual()[index]),actualX,yy+8,data.actual()[index]>0?0xFF8DEA9A:0xFFFFFFFF,true);g.drawString(font,"+"+ResourceSlots.compact(data.maximum()[index]),maxX,yy+8,0xFFFFFFFF,true);g.drawString(font,"-"+ResourceSlots.compact(data.upkeep()[index]),dueX,yy+8,data.upkeep()[index]>0?0xFFFFCCCC:0xFFFFFFFF,true);}
            }else {List<ProductionNetwork.Source> list=sources();page=Math.min(page,Math.max(0,(list.size()-1)/visible()));g.drawString(font,list.size()+" sources / potential +"+data.maximum()[selected]+" per 6 min",x+12,y+47,0xFF404040,false);
                for(int row=0;row<visible()&&page*visible()+row<list.size();row++){var source=list.get(page*visible()+row);int yy=y+61+row*29;MinecraftMenu.inset(g,x+8,yy,x+w-8,yy+26);g.drawString(font,font.plainSubstrByWidth(source.name()+"  +"+source.maximum()[selected]+"/6m  |  next "+source.seconds()+"s",w-24),x+12,yy+3,0xFFFFFFFF,true);g.drawString(font,font.plainSubstrByWidth(source.status(),w-24),x+12,yy+15,source.running()?0xFF8DEA9A:0xFFFFAAAA,true);if(mx>=x+8&&mx<x+w-8&&my>=yy&&my<yy+26)g.renderComponentTooltip(font,List.of(Component.literal(source.location()),Component.literal(source.status())),mx,my);}
                if(list.isEmpty())g.drawString(font,"No registered producer for this resource.",x+12,y+72,0xFF404040,false);
            }
            g.drawString(font,font.plainSubstrByWidth(data.full()?"STORAGE FULL: automatic output is paused":"Last 60s = delivered; Max/6m = potential; upkeep / 5 min",w-24),x+12,y+h-53,data.full()?0xFFA01919:0xFF404040,false);
            super.render(g,mx,my,partial);
        }
    }
}
