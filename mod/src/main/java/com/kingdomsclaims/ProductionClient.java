package com.kingdomsclaims;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
/** Plain resource totals first; individual producers and timers on selection. */
public final class ProductionClient {
    private static Screen returnTo;private static boolean pendingOpen=false;
    public static boolean active(){return pendingOpen||Minecraft.getInstance().screen instanceof ProductionScreen;}
    public static void expect(){if(!(Minecraft.getInstance().screen instanceof ProductionScreen))pendingOpen=true;}
    public static void accept(ProductionNetwork.View data){var mc=Minecraft.getInstance();if(mc.screen instanceof ProductionScreen s)s.data=data;else if(pendingOpen){pendingOpen=false;returnTo=mc.screen;mc.setScreen(new ProductionScreen(data));}}
    private static final int[] ORDER={0,2,3,1,4,5,6,7};
    private static final class ProductionScreen extends Screen {
        private ProductionNetwork.View data;private int selected=-1,page=0,ticks=0;
        ProductionScreen(ProductionNetwork.View data){super(Component.literal("Kingdom Production"));this.data=data;}
        int w(){return Math.min(440,width-12);}int h(){return Math.min(320,height-12);}int x(){return (width-w())/2;}int y(){return (height-h())/2;}int visible(){return Math.max(1,(h()-122)/40);}
        List<ProductionNetwork.Source> sources(){return data.sources().stream().filter(s->selected>=0&&(s.mask()&(1<<selected))!=0).toList();}
        int taxes(){return ProductionNetwork.expected(data.sources(),300)[7];}
        void rebuild(){clearWidgets();init();}
        void button(String label,int x,int y,int w,Runnable r){addRenderableWidget(Button.builder(Component.literal(label),b->r.run()).bounds(x,y,w,20).build());}
        @Override protected void init(){int x=x(),y=y()+h()-28,w=w();button("<",x+10,y,30,()->page=Math.max(0,page-1));button(">",x+44,y,30,()->{int total=selected<0?8:sources().size();page=Math.min(Math.max(0,(total-1)/visible()),page+1);});button(selected<0?"Back":"All resources",x+w/2-53,y,106,()->{if(selected<0)onClose();else{selected=-1;page=0;rebuild();}});button("Refresh",x+w-84,y,74,ProductionNetwork::request);}
        @Override public void tick(){if(++ticks%40==0)ProductionNetwork.request();}
        @Override public void onClose(){pendingOpen=false;Minecraft.getInstance().setScreen(returnTo);}
        @Override public boolean isPauseScreen(){return false;}
        @Override public boolean mouseClicked(double mx,double my,int button){if(super.mouseClicked(mx,my,button))return true;if(button==0&&selected<0&&mx>=x()+8&&mx<x()+w()-8&&my>=y()+66&&my<y()+66+visible()*40){int row=(int)(my-y()-66)/40+page*visible();if(row<8){selected=ORDER[row];page=0;rebuild();return true;}}return false;}
        private void text(GuiGraphics g,String text,int x,int y,int max,int color){g.drawString(font,font.plainSubstrByWidth(text,max),x,y,color,false);}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){renderBackground(g);int x=x(),y=y(),w=w(),h=h();MinecraftMenu.panel(g,x,y,x+w,y+h);
            text(g,selected<0?"KINGDOM PRODUCTION":ProductionText.NAMES[selected].toUpperCase()+" PRODUCERS",x+12,y+10,w-24,MinecraftMenu.TEXT);
            if(selected<0){text(g,"Stored = available now. Gain = expected production.",x+12,y+27,w-24,MinecraftMenu.TEXT);text(g,"Click a resource to see where it comes from.",x+12,y+43,w-24,MinecraftMenu.TEXT);
                page=Math.min(page,Math.max(0,7/visible()));
                for(int row=0;row<visible()&&page*visible()+row<8;row++){
                    int index=ORDER[page*visible()+row],yy=y+66+row*40,halves=index==7?taxes():data.maximum()[index];
                    MinecraftMenu.inset(g,x+8,yy,x+w-8,yy+36);g.fill(x+9,yy+1,x+w-9,yy+35,0xFFC6C6C6);
                    g.renderItem(new ItemStack(EconomyService.goodsItem(OperationsData.GOODS[index])),x+13,yy+9);
                    text(g,ProductionText.NAMES[index],x+36,yy+5,w/2-30,MinecraftMenu.TEXT);
                    text(g,"Stored: "+ProductionText.count(data.stock()[index]),x+w/2,yy+5,w/2-14,MinecraftMenu.TEXT);
                    text(g,data.full()?"Storage full - production paused":"Gain: "+ProductionText.rate(halves,index==7?300:360),x+36,yy+21,w-52,data.full()?MinecraftMenu.RED:MinecraftMenu.GREEN);
                }
            }else{
                text(g,"Stored: "+ProductionText.count(data.stock()[selected]),x+12,y+27,w-24,MinecraftMenu.TEXT);
                text(g,"Upkeep cost: "+ProductionText.count(data.upkeep()[selected])+" every 5 minutes",x+12,y+43,w-24,MinecraftMenu.TEXT);
                List<ProductionNetwork.Source> list=sources();page=Math.min(page,Math.max(0,(list.size()-1)/visible()));
                for(int row=0;row<visible()&&page*visible()+row<list.size();row++){
                    var source=list.get(page*visible()+row);int yy=y+66+row*40;
                    MinecraftMenu.inset(g,x+8,yy,x+w-8,yy+36);g.fill(x+9,yy+1,x+w-9,yy+35,0xFFC6C6C6);
                    String title=ProductionText.name(source.name())+": "+ProductionText.rate(source.maximum()[selected],source.interval());
                    text(g,title,x+13,yy+4,w-26,MinecraftMenu.TEXT);
                    text(g,source.running()?"Next cycle in "+ProductionText.time(source.seconds()):"PAUSED - "+source.status().replace("Paused: ",""),x+13,yy+15,w-26,source.running()?MinecraftMenu.GREEN:MinecraftMenu.RED);
                    text(g,source.location(),x+13,yy+26,w-26,MinecraftMenu.MUTED);
                    if(mx>=x+8&&mx<x+w-8&&my>=yy&&my<yy+36)g.renderComponentTooltip(font,List.of(Component.literal(source.location()),Component.literal(source.status()),Component.literal("Kingdom total delivered in last 60 seconds: "+ProductionText.count(data.actual()[selected]))),mx,my);
                }
                if(list.isEmpty())text(g,"No producer for this resource.",x+12,y+76,w-24,MinecraftMenu.TEXT);
            }
            text(g,data.full()?"STORAGE FULL: add storage or withdraw resources.":"Rates use the latest scan. Half-items carry over.",x+12,y+h-48,w-24,data.full()?MinecraftMenu.RED:MinecraftMenu.TEXT);
            int total=selected<0?8:sources().size();text(g,"Page "+(page+1)+" / "+Math.max(1,(total+visible()-1)/visible()),x+w-86,y+h-61,76,MinecraftMenu.MUTED);
            super.render(g,mx,my,partial);
        }
    }
}
