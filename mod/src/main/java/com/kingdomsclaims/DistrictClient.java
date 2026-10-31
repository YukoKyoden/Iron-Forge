package com.kingdomsclaims;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;

/** One-screen customizable city zoning map. No registered houses or civilian entities. */
public final class DistrictClient {
    private DistrictClient(){}
    public static boolean isOpen(){return Minecraft.getInstance().screen instanceof ManagerScreen;}
    public static void accept(DistrictNetwork.Snapshot data){openTab(data,false);}
    public static void openTab(DistrictNetwork.Snapshot data,boolean economy){var mc=Minecraft.getInstance();
        if(mc.screen instanceof ManagerScreen s&&s.data.manager().equals(data.manager()))s.apply(data);
        else mc.setScreen(new ManagerScreen(data,economy));
    }
    private static final int[] COLORS={0xFF71BCE7,0xFFE8C16C,0xFFCF8976,0xFF87C976};
    private static final class ManagerScreen extends Screen {
        private DistrictNetwork.Snapshot data;
        private UUID zoneId=null;private int type=0,zoneScroll=0;private boolean economy=false,dragging=false,painting=false;
        private final LinkedHashSet<Long> brush=new LinkedHashSet<>();
        private double viewX,viewZ,zoom=15;
        private String lastMessage="";
        private EditBox name;
        private ManagerScreen(DistrictNetwork.Snapshot data,boolean economy){super(Component.literal("Kingdom Planner"));this.data=data;this.economy=economy;viewX=data.cx();viewZ=data.cz();}
        private void apply(DistrictNetwork.Snapshot d){
            String nameText=name==null?"":name.getValue();data=d;
            if(zoneId!=null&&d.districts().stream().noneMatch(z->z.id().equals(zoneId)))zoneId=null;
            brush.clear();lastMessage=d.message();clearWidgets();init();if(name!=null&&!nameText.isBlank())name.setValue(nameText);
        }
        private DistrictNetwork.ZoneInfo selectedZone(){for(var z:data.districts())if(z.id().equals(zoneId))return z;return null;}
        private int sideX(){return Math.max(240,width-277);}
        private int left(){return 8;}private int right(){return sideX()-9;}private int top(){return 39;}private int bottom(){return height-42;}
        private double midX(){return (left()+right())/2.0;}private double midY(){return (top()+bottom())/2.0;}
        private int centerX(){return (int)Math.round(viewX);}private int centerZ(){return (int)Math.round(viewZ);}
        private double px(double cx){return midX()+(cx-viewX)*zoom;}
        private double py(double cz){return midY()+(cz-viewZ)*zoom;}
        private boolean mapHit(double mx,double my){return mx>=left()&&mx<right()&&my>=top()&&my<bottom();}
        private void refresh(){DistrictNetwork.CHANNEL.sendToServer(new DistrictNetwork.Request(data.manager(),centerX(),centerZ()));}
        private void submit(String task){
            long[] selected=brush.stream().mapToLong(Long::longValue).toArray();
            if((task.equals("create")||task.equals("paint")||task.equals("erase"))&&selected.length==0){lastMessage="Paint claimed chunks on the map first.";return;}
            DistrictNetwork.CHANNEL.sendToServer(new DistrictNetwork.Action(data.manager(),task,zoneId==null?new UUID(0,0):zoneId,name==null?"":name.getValue(),type,selected,centerX(),centerZ()));
        }
        private void button(String label,int x,int y,int w,String tooltip,Runnable action,boolean available){
            if(y+20>height-10)return;
            var b=Button.builder(Component.literal(label),x0->action.run()).bounds(x,y,Math.max(25,w),20).build();
            b.active=available;b.setTooltip(Tooltip.create(Component.literal(tooltip)));addRenderableWidget(b);
        }
        private String clip(String s,int pixels){return font.plainSubstrByWidth(s,Math.max(10,pixels));}
        private void outline(GuiGraphics g,int x,int y,int w,int h,int c){g.fill(x-1,y-1,x+w+1,y,c);g.fill(x-1,y+h,x+w+1,y+h+1,c);g.fill(x-1,y,x,y+h,c);g.fill(x+w,y,x+w+1,y+h,c);}
        @Override protected void init(){
            int sx=sideX()+8,sw=width-sx-10,half=(sw-6)/2;
            button("+",left()+2,12,22,"Zoom in",()->zoom=Math.min(38,zoom*1.25),true);
            button("-",left()+27,12,22,"Zoom out",()->zoom=Math.max(5,zoom/1.25),true);
            button("Home",left()+53,12,49,"Center on manager",()->{viewX=data.manager().getX()/16.;viewZ=data.manager().getZ()/16.;refresh();},true);
            button("Refresh",left()+106,12,59,"Reload saved zoning and terrain",this::refresh,true);
            button("X",width-27,9,20,"Close manager",this::onClose,true);
            button("Back to 3D City",sx,9,Math.max(80,sw-30),"Return to the Kingdom Planner's 3D view",()->KingdomOverviewClient.open(data),true);
            button(economy?"DISTRICTS":"▶ DISTRICTS",sx,48,half,"Paint and manage district chunks",()->{economy=false;clearWidgets();init();},true);
            button(economy?"▶ ECONOMY":"ECONOMY",sx+half+6,48,sw-half-6,"Gold, taxes, people and development",()->{economy=true;clearWidgets();init();},true);
            if(economy){
                button("Gold nuggets (up to 64)",sx,167,sw,"Deposit nuggets to your local warehouse",()->{type=0;submit("gold");},true);
                button("Gold ingots (up to 64)",sx,191,sw,"Deposit ingots to your local warehouse",()->{type=1;submit("gold");},true);
                button("Deposit 64 iron",sx,220,half,"Fund developments and troop upkeep",()->submit("depositiron"),true);
                button("Deposit 64 wheat",sx+half+6,220,sw-half-6,"Fund population and troop upkeep",()->submit("depositwheat"),true);
                button("Tax -5%",sx,256,half,"Lower taxation and increase satisfaction",()->{type=Math.max(0,data.tax()-5);submit("tax");},true);
                button("Tax +5%",sx+half+6,256,sw-half-6,"Raise taxation, potentially reducing satisfaction",()->{type=Math.min(25,data.tax()+5);submit("tax");},true);
                button("Refresh balances",sx,287,sw,"Update treasury and development status",this::refresh,true);
                return;
            }
            name=new EditBox(font,sx,83,sw,18,Component.literal("District name"));name.setMaxLength(28);
            name.setValue(selectedZone()!=null?selectedZone().name():"Old Town");addRenderableWidget(name);
            button("Type: "+DistrictData.TYPES[type],sx,107,sw,"Click to cycle residential, commercial, industrial, agriculture",()->{type=(type+1)%4;clearWidgets();init();},true);
            button("Create",sx,132,half,"Create a named district using painted chunks (initially level 0)",()->submit("create"),true);
            button("Add painted",sx+half+6,132,sw-half-6,"Add highlighted chunks to selected district",()->submit("paint"),zoneId!=null);
            button("Erase painted",sx,157,half,"Remove highlighted chunks from selected district",()->submit("erase"),zoneId!=null);
            button("Clear brush",sx+half+6,157,sw-half-6,"Clear selected chunks without changing the world",()->brush.clear(),true);
            button("New district",sx,182,half,"Clear current district and begin a new one",()->{zoneId=null;brush.clear();clearWidgets();init();},true);
            button("Develop +1",sx+half+6,182,sw-half-6,"Invest iron/wheat to raise level, population capacity and jobs",()->submit("upgrade"),zoneId!=null);
            button("Delete district",sx,207,sw,"Delete selected district without refunding its development",()->submit("delete"),zoneId!=null);
            int rowY=252,visible=Math.max(0,(height-rowY-38)/23);
            for(int i=zoneScroll;i<Math.min(data.districts().size(),zoneScroll+visible);i++){
                var z=data.districts().get(i);int y=rowY+(i-zoneScroll)*23;
                button(clip((z.id().equals(zoneId)?"▶ ":"")+z.name()+" L"+z.level()+" ("+z.chunks()+")",sw-12),sx,y,sw,"Select district "+z.name(),()->{zoneId=z.id();type=z.type();brush.clear();clearWidgets();init();},true);
            }
            if(height>320){button("▲",sx,height-30,half,"Scroll district list up",()->{zoneScroll=Math.max(0,zoneScroll-1);clearWidgets();init();},zoneScroll>0);
                button("▼",sx+half+6,height-30,sw-half-6,"Scroll district list down",()->{zoneScroll=Math.min(Math.max(0,data.districts().size()-visible),zoneScroll+1);clearWidgets();init();},zoneScroll+visible<data.districts().size());}
        }
        private int tileIndex(int cx,int cz){int x=cx-data.cx()+DistrictNetwork.SIZE/2,z=cz-data.cz()+DistrictNetwork.SIZE/2;return x<0||z<0||x>=DistrictNetwork.SIZE||z>=DistrictNetwork.SIZE?-1:z*DistrictNetwork.SIZE+x;}
        private boolean validOwner(int cx,int cz){int index=tileIndex(cx,cz);return index>=0&&data.claims()[index]==1;}
        private void paintAt(double mx,double my,boolean toggle){
            int cx=(int)Math.floor(viewX+(mx-midX())/zoom),cz=(int)Math.floor(viewZ+(my-midY())/zoom);
            if(!validOwner(cx,cz)){lastMessage="Only directly claimed chunks in your faction can be zoned.";return;}
            long k=ChunkPos.asLong(cx,cz);if(toggle&&brush.contains(k))brush.remove(k);else if(brush.size()<DistrictData.MAX_PAINT)brush.add(k);
            else lastMessage="Brush limit is 128 chunks per operation.";
        }
        private void drawMap(GuiGraphics g){
            g.fill(left(),top(),right(),bottom(),0xFF182123);
            g.enableScissor(left(),top(),right(),bottom());int sub=WarMapData.SUB;
            for(int z=0;z<DistrictNetwork.SIZE;z++)for(int x=0;x<DistrictNetwork.SIZE;x++){
                int chunkX=data.cx()+x-DistrictNetwork.SIZE/2,chunkZ=data.cz()+z-DistrictNetwork.SIZE/2;
                double sx=px(chunkX),sy=py(chunkZ);if(sx>right()||sy>bottom()||sx+zoom<left()||sy+zoom<top())continue;
                int index=z*DistrictNetwork.SIZE+x,base=index*sub*sub;
                for(int v=0;v<sub;v++)for(int u=0;u<sub;u++){
                    int col=data.terrain()[base+v*sub+u]&0xffff;
                    int rgb=col==WarMapData.UNKNOWN?0xFF283134:0xFF000000|((col>>8&15)*17<<16)|((col>>4&15)*17<<8)|(col&15)*17;
                    int a=(int)Math.floor(sx+u*zoom/sub),b=(int)Math.floor(sy+v*zoom/sub),c=(int)Math.ceil(sx+(u+1)*zoom/sub),d=(int)Math.ceil(sy+(v+1)*zoom/sub);
                    g.fill(a,b,c,d,rgb);
                }
                int sx1=(int)Math.floor(sx),sy1=(int)Math.floor(sy),sx2=(int)Math.ceil(sx+zoom),sy2=(int)Math.ceil(sy+zoom);
                int claim=data.claims()[index];if(claim==1)g.fill(sx1,sy1,sx2,sy2,0x2645E184);else if(claim==2)g.fill(sx1,sy1,sx2,sy2,0x32E66E68);
                int owner=Byte.toUnsignedInt(data.zoning()[index]);
                if(owner>0&&owner<=data.districts().size()){
                    var zInfo=data.districts().get(owner-1);int clr=COLORS[zInfo.type()];
                    g.fill(sx1,sy1,sx2,sy2,(0x65<<24)|(clr&0xFFFFFF));
                    if(zInfo.id().equals(zoneId))outline(g,sx1,sy1,Math.max(1,sx2-sx1),Math.max(1,sy2-sy1),0xFFFFFFFF);
                }
                if(brush.contains(ChunkPos.asLong(chunkX,chunkZ))){g.fill(sx1,sy1,sx2,sy2,0x66FFFFFF);outline(g,sx1,sy1,Math.max(1,sx2-sx1),Math.max(1,sy2-sy1),0xFFFFFFD0);}
            }
            int cx=(int)Math.round(px(data.manager().getX()/16.)),cy=(int)Math.round(py(data.manager().getZ()/16.));
            g.fill(cx-3,cy-3,cx+4,cy+4,0xFF101010);g.fill(cx-1,cy-1,cx+2,cy+2,0xFFFFFFFF);
            g.disableScissor();
        }
        private void sidebar(GuiGraphics g){
            int sx=sideX(),inner=sx+8,sw=width-inner-10;
            MinecraftMenu.panel(g,sx,0,width,height);
            g.drawString(font,"KINGDOM MANAGER",inner,9,0xFF404040,false);
            g.drawString(font,clip(data.faction()+" ("+data.factionType()+")",sw),inner,25,0xFF404040,false);
            if(economy){
                g.drawString(font,"GOLD INGOTS  "+data.gold(),inner,80,0xFF404040,false);
                g.drawString(font,"Iron "+data.iron()+"    Wheat "+data.wheat(),inner,96,0xFF404040,false);
                g.drawString(font,"Population "+data.population()+" / "+data.capacity(),inner,113,0xFF246B26,false);
                g.drawString(font,"Jobs "+data.jobs()+"    Food capacity "+data.food(),inner,128,0xFF246B26,false);
                g.drawString(font,"Taxes "+data.tax()+"%  (next "+data.income()+" gold nuggets)",inner,143,0xFF404040,false);
                if(height>343){
                    g.drawString(font,"Wages / 5m: "+data.payroll()+" gold nuggets",inner,320,0xFF404040,false);
                    g.drawString(font,"Next charge: "+data.seconds()/60+":"+String.format("%02d",data.seconds()%60),inner,334,0xFF404040,false);
                    g.drawString(font,"Last funded: "+(data.funded()?"YES":"NO")+"    Happiness: "+data.happy()+"%",inner,348,data.funded()?0xFF246B26:0xFFA01919,false);
                }
            }else{
                g.drawString(font,"Name district / paint owned chunks",inner,72,0xFF404040,false);
                g.drawString(font,"Painted: "+brush.size()+" / 128",inner,236,0xFF404040,false);
                if(height>290)g.drawString(font,"Districts: "+data.districts().size()+" / 48",inner,245,0xFF404040,false);
            }
        }
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            renderBackground(g);MinecraftMenu.panel(g,0,0,width,height);drawMap(g);sidebar(g);
            g.drawString(font,"DISTRICT ZONING",left()+3,3,0xFF404040,false);
            g.drawString(font,"Click/drag: paint chunks | Right-drag: pan | Wheel: zoom",left()+4,height-32,0xFF404040,false);
            g.drawString(font,"Blue residential / Gold commerce / Red industry / Green farms",left()+4,height-19,0xFF404040,false);
            if(mapHit(mx,my)){
                int cx=(int)Math.floor(viewX+(mx-midX())/zoom),cz=(int)Math.floor(viewZ+(my-midY())/zoom);
                g.drawString(font,"Chunk "+cx+", "+cz,left()+6,top()+6,0xFFFFFFFF,true);
            }
            String msg=lastMessage.isEmpty()?data.message():lastMessage;
            if(!msg.isBlank())g.drawCenteredString(font,clip(msg,Math.max(100,right()-14)),(left()+right())/2,height-7,0xFFFFFFFF);
            super.render(g,mx,my,partial);
            int sx=sideX()+8,sw=width-sx-10,half=(sw-6)/2;
            outline(g,economy?sx+half+6:sx,48,economy?sw-half-6:half,20,0xFFFFFFFF);
            if(!economy&&zoneId!=null)for(int i=zoneScroll;i<data.districts().size();i++)if(data.districts().get(i).id().equals(zoneId)){
                int y=252+(i-zoneScroll)*23;if(y>=252&&y+20<height-30)outline(g,sx,y,sw,20,0xFFFFFFFF);
            }
        }
        @Override public boolean mouseClicked(double mx,double my,int button){
            // Buttons and text fields must win hit-testing; never paint or march underneath a control.
            if(super.mouseClicked(mx,my,button))return true;
            if(button==1&&mapHit(mx,my)){dragging=true;return true;}
            if(button==0&&mapHit(mx,my)){painting=true;paintAt(mx,my,true);return true;}
            return false;
        }
        @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){
            if(button==1&&dragging){viewX-=dx/zoom;viewZ-=dy/zoom;return true;}
            if(button==0&&painting&&mapHit(mx,my)){paintAt(mx,my,false);return true;}
            return super.mouseDragged(mx,my,button,dx,dy);
        }
        @Override public boolean mouseReleased(double mx,double my,int button){
            if(button==1&&dragging){dragging=false;refresh();return true;}
            if(button==0&&painting){painting=false;return true;}
            return super.mouseReleased(mx,my,button);
        }
        @Override public boolean mouseScrolled(double mx,double my,double delta){
            if(mapHit(mx,my)){zoom=Math.max(5,Math.min(38,zoom*(delta>0?1.2:1/1.2)));return true;}
            if(mx>sideX()&&!economy){int visible=Math.max(1,(height-252-38)/23);zoneScroll=Math.max(0,Math.min(Math.max(0,data.districts().size()-visible),zoneScroll+(delta<0?1:-1)));clearWidgets();init();return true;}
            return super.mouseScrolled(mx,my,delta);
        }
        @Override public boolean isPauseScreen(){return false;}
    }
}
