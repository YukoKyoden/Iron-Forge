package com.kingdomsclaims;

import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** One-screen War Table: terrain left, army roster/orders right. */
public final class WarMapClient {
    private WarMapClient(){}
    private static final String[] UNIFORMS={"Crimson","Royal Blue","Forest","Sand"};
    public static void acceptOperations(OperationsNetwork.Snapshot snap){
        var mc=Minecraft.getInstance();
        if(mc.screen instanceof TacticalScreen screen&&screen.data.table().equals(snap.table()))screen.applyOperations(snap);
    }
    public static void accept(WarMapNetwork.Snapshot snapshot){
        var mc=Minecraft.getInstance();
        if(mc.screen instanceof TacticalScreen open&&open.data.table().equals(snapshot.table())){open.apply(snapshot);return;}
        mc.setScreen(new TacticalScreen(snapshot));
    }
    private static final class TacticalScreen extends Screen {
        private WarMapNetwork.Snapshot data;
        private UUID selected,selectedSite,selectedCaravan;
        private OperationsNetwork.Snapshot operations;
        private boolean missions=false,treasury=false;
        private int goodIndex=0,caravanX,caravanZ;
        private double viewX,viewZ,zoom=20.0;
        private boolean dragging=false,dragMoved=false,showClaims=true;private int refreshTicks=0;private double dragStartX,dragStartY;private String nameDraft="";
        private int rosterScroll=0,actionScroll=0;
        private String localMessage="";
        private EditBox armyName;
        TacticalScreen(WarMapNetwork.Snapshot snap){
            super(Component.literal("War Table"));data=snap;
            viewX=snap.cx();viewZ=snap.cz();
            if(!snap.armies().isEmpty())selected=snap.armies().get(0).id();
        }
        void applyOperations(OperationsNetwork.Snapshot snap){
            if(armyName!=null)nameDraft=armyName.getValue();operations=snap;
            if(selectedSite!=null&&snap.sites().stream().noneMatch(x->x.id().equals(selectedSite)))selectedSite=null;
            if(selectedCaravan!=null&&snap.caravans().stream().noneMatch(x->x.id().equals(selectedCaravan)))selectedCaravan=null;
            clearWidgets();init();
        }
        private OperationsNetwork.SiteInfo site(){if(operations!=null)for(var s:operations.sites())if(s.id().equals(selectedSite))return s;return null;}
        private OperationsNetwork.CaravanInfo caravan(){if(operations!=null)for(var c:operations.caravans())if(c.id().equals(selectedCaravan))return c;return null;}
        private String selectedGood(){return OperationsData.GOODS[goodIndex%OperationsData.GOODS.length];}
        private void operationCommand(String action,String payload){OperationsNetwork.CHANNEL.sendToServer(new OperationsNetwork.Action(data.table(),action,payload,centerChunkX(),centerChunkZ()));}
        private void siteCommand(String action){if(selected!=null&&selectedSite!=null)operationCommand(action,selected+"|"+selectedSite);}
        private void caravanCommand(String action){if(selected!=null&&selectedCaravan!=null)operationCommand(action,selected+"|"+selectedCaravan);}
        void apply(WarMapNetwork.Snapshot snap){
            String name=armyName==null?nameDraft:armyName.getValue();
            var previous=data;data=snap;
            if(snap.message().startsWith("Created "))for(var candidate:snap.armies())if(previous.armies().stream().noneMatch(old->old.id().equals(candidate.id())))selected=candidate.id();
            if(selected==null||snap.armies().stream().noneMatch(a->a.id().equals(selected)))selected=snap.armies().isEmpty()?null:snap.armies().get(0).id();
            clearWidgets();init();if(armyName!=null)armyName.setValue(name);
        }
        // The roster, selected-army summary and orders each own a non-overlapping area.
        // Heights are derived from the current GUI scale instead of hard-coded y=170/196.
        private int rosterTop(){return 111;}
        private int rosterRows(){return Math.max(1,Math.min(4,(height-316)/23));}
        private int summaryTop(){return rosterTop()+rosterRows()*23+6;}
        private int summaryHeight(){return height<300?16:48;}
        private int ordersLabelY(){return summaryTop()+summaryHeight()+6;}
        private int ordersTop(){return ordersLabelY()+14;}
        private int ordersBottom(){return height-39;}
        private int visibleOrderRows(){
            int available=ordersBottom()-ordersTop();
            return available<20?0:Math.min(10,1+(available-20)/24);
        }
        private int maxActionScroll(){return Math.max(0,missions?11-visibleOrderRows():10-visibleOrderRows());}
        private int sideX(){return Math.max(235,width-248);}
        private int mapLeft(){return 8;}
        private int mapTop(){return 40;}
        private int mapRight(){return sideX()-8;}
        private int mapBottom(){return Math.max(mapTop()+50,height-47);}
        private double midX(){return (mapLeft()+mapRight())/2.0;}
        private double midY(){return (mapTop()+mapBottom())/2.0;}
        private double screenX(int blockX){return midX()+(blockX/16.0-viewX)*zoom;}
        private double screenY(int blockZ){return midY()+(blockZ/16.0-viewZ)*zoom;}
        private boolean insideMap(double x,double y){return x>=mapLeft()&&x<mapRight()&&y>=mapTop()&&y<mapBottom();}
        private int centerChunkX(){return (int)Math.round(viewX);}
        private int centerChunkZ(){return (int)Math.round(viewZ);}
        private void refresh(){WarMapNetwork.CHANNEL.sendToServer(new WarMapNetwork.Request(data.table(),centerChunkX(),centerChunkZ()));}
        private void command(String action,String payload){
            WarMapNetwork.CHANNEL.sendToServer(new WarMapNetwork.Action(data.table(),action,payload,centerChunkX(),centerChunkZ()));
        }
        private void commandSelected(String action){if(selected!=null)command(action,selected.toString());}
        private void commandSelected(String action,String payload){if(selected!=null)command(action,selected+"|"+payload);}
        private WarTableNetwork.ArmyInfo army(){
            for(var a:data.armies())if(a.id().equals(selected))return a;
            return null;
        }
        private boolean managerOrigin(){
            Minecraft mc=Minecraft.getInstance();
            return mc.level!=null&&mc.level.getBlockState(data.table()).is(KingdomsClaims.KINGDOM_MANAGER.get());
        }
        private void button(String label,int x,int y,int w,String help,Runnable action,boolean enabled){
            if(y<0||y+20>height-2)return;
            var b=Button.builder(Component.literal(label),v->action.run()).bounds(x,y,w,20).build();
            b.active=enabled;b.setTooltip(Tooltip.create(Component.literal(help)));addRenderableWidget(b);
        }
        private String clip(String s,int pixels){return font.plainSubstrByWidth(s,Math.max(15,pixels));}
        @Override protected void init(){
            int sx=sideX()+7,sw=width-sx-8;int half=(sw-5)/2;
            button("+",mapLeft()+4,12,23,"Zoom in toward map center",()->zoom=Math.min(42,zoom*1.3),true);
            button("-",mapLeft()+30,12,23,"Zoom out",()->zoom=Math.max(2,zoom/1.3),true);
            button("Home",mapLeft()+57,12,48,"Center on command building",()->{viewX=data.table().getX()/16.0;viewZ=data.table().getZ()/16.0;refresh();},true);
            button("Borders",mapLeft()+108,12,56,"Toggle territory outlines",()->showClaims=!showClaims,true);
            button("Refresh",mapLeft()+167,12,58,"Refresh explored terrain and army positions",this::refresh,true);
            if(managerOrigin())button("City View",width-112,9,81,"Return to the 3D Kingdom Planner",()->KingdomOverviewClient.returnFromWarMap(data.table()),true);
            button("X",width-26,9,20,"Close map to Minecraft",this::onClose,true);
            int third=(sw-10)/3;
            button(treasury?"ARMIES":missions?"ARMIES":"▶ ARMIES",sx,69,third,"Military orders",()->{missions=false;treasury=false;actionScroll=0;clearWidgets();init();},true);
            button(!treasury&&missions?"▶ OPS":"OPS",sx+third+5,69,third,"Expeditions, resource sites, outposts and caravans",()->{missions=true;treasury=false;actionScroll=0;clearWidgets();init();},true);
            button(treasury?"▶ BANK":"STOCK",sx+(third+5)*2,69,sw-(third+5)*2,"Local warehouse iron, wheat and five-minute upkeep",()->{treasury=true;missions=false;actionScroll=0;clearWidgets();init();},true);
            if(treasury){
                int y=136;
                // These buttons stay visible in the Treasury tab; they never require scrolling or army selection.
                button("Iron ≤16",sx,y,half,"Deposit up to 16 iron ingots you have",()->operationCommand("depositmax","IRON|16"),data.authorized());
                button("Iron ≤64",sx+half+5,y,half,"Deposit up to 64 iron ingots you have",()->operationCommand("depositmax","IRON|64"),data.authorized());
                y+=26;
                button("Wheat ≤16",sx,y,half,"Deposit up to 16 wheat you have",()->operationCommand("depositmax","WHEAT|16"),data.authorized());
                button("Wheat ≤64",sx+half+5,y,half,"Deposit up to 64 wheat you have",()->operationCommand("depositmax","WHEAT|64"),data.authorized());
                y+=26;
                button("Deposit ALL iron",sx,y,half,"Store carried iron at your local physical warehouse",()->operationCommand("depositmax","IRON|2304"),data.authorized());
                button("Deposit ALL wheat",sx+half+5,y,half,"Store carried wheat at your local physical warehouse",()->operationCommand("depositmax","WHEAT|2304"),data.authorized());
                y+=32;
                button("Refresh balances",sx,y,sw,"Refresh treasury balances, payment history and countdown",this::refresh,true);
                button("Return to Operations",sx,y+26,sw,"Back to resource sites and caravans",()->{treasury=false;missions=true;clearWidgets();init();},true);
                return;
            }
            armyName=new EditBox(font,sx,48,Math.max(75,sw-67),18,Component.literal("New army name"));
            armyName.setValue(nameDraft);armyName.setMaxLength(24);armyName.setHint(Component.literal("New army name"));addRenderableWidget(armyName);
            button("Create",width-73,47,57,"Create up to 15 named armies",()->command("create",armyName.getValue()),data.authorized());
            int top=rosterTop();
            int count=rosterRows();
            rosterScroll=Math.max(0,Math.min(rosterScroll,Math.max(0,data.armies().size()-count)));
            for(int i=rosterScroll;i<Math.min(data.armies().size(),rosterScroll+count);i++){
                var a=data.armies().get(i);var chosen=a;
                int y=top+(i-rosterScroll)*23;
                button((a.id().equals(selected)?"▶▶ ":"")+clip(a.name()+" — "+(a.strength()+a.fielded())+" troops",sw-12),sx,y,sw,
                    "Select "+a.name()+" to command and customize",()->{selected=chosen.id();actionScroll=0;clearWidgets();init();},true);
            }
            // Scroll arrows live on the roster heading, never above the selected-army summary.
            button("▲",width-59,91,24,"Previous armies",()->{rosterScroll=Math.max(0,rosterScroll-1);clearWidgets();init();},rosterScroll>0);
            button("▼",width-33,91,24,"More armies",()->{rosterScroll=Math.min(Math.max(0,data.armies().size()-count),rosterScroll+1);clearWidgets();init();},rosterScroll+count<data.armies().size());
            var a=army();boolean ok=data.authorized()&&a!=null;
            // Commands start BELOW the entire roster and selected-army summary.
            int actionsTop=ordersTop(),rowsBottom=ordersBottom();
            actionScroll=Math.max(0,Math.min(actionScroll,maxActionScroll()));
            for(int row=actionScroll;row<(missions?11:10);row++){
                int y=actionsTop+(row-actionScroll)*24;
                if(y+20>rowsBottom)break;
                if(missions){
                    var site=site();var caravan=caravan();boolean canSite=ok&&site!=null;
                    switch(row){
                        case 0->{
                            button("Scout",sx,y,half,"Send 5+ troops to identify exact yield and duration",()->siteCommand("scout"),canSite);
                            button("Extract",sx+half+5,y,half,"Commit 10+ troops to extract loot or resources",()->siteCommand("harvest"),canSite);
                        }
                        case 1->{
                            button("Outpost",sx,y,half,"Establish a defended wilderness watch post using 10+ troops",()->siteCommand("outpost"),canSite);
                            button("Take Over",sx+half+5,y,half,"Attack rival operation; winner inherits progress and stockpile",()->siteCommand("contest"),canSite&&site.rival());
                        }
                        case 2->{
                            button("Abort",sx,y,half,"Leave stockpile and progress at risk, retreat on foot",()->siteCommand("abort"),canSite&&site.friendly());
                            button("Recall Outpost",sx+half+5,y,half,"Withdraw your outpost garrison",()->siteCommand("recalloutpost"),canSite&&site.friendly());
                        }
                        case 3->{
                            button("Cargo: "+selectedGood()+" ("+(operations==null?0:operations.warehouse()[goodIndex])+")",sx,y,sw,"Cycle warehouse resources for collection or shipping",()->{goodIndex=(goodIndex+1)%OperationsData.GOODS.length;clearWidgets();init();},true);
                        }
                        case 4->{
                            button("Collect 16",sx,y,half,"Move 16 delivered goods from faction warehouse to inventory",()->operationCommand("withdraw",selectedGood()+"|16"),operations!=null&&operations.warehouse()[goodIndex]>=16);
                            button("Collect 64",sx+half+5,y,half,"Collect up to a stack from your warehouse",()->operationCommand("withdraw",selectedGood()+"|64"),operations!=null&&operations.warehouse()[goodIndex]>=64);
                        }
                        case 5->{
                            button("Caravan 16",sx,y,half,"Escort 16 warehouse resources to clicked friendly or affiliated territory",()->{if(selected!=null)operationCommand("sendcaravan",selected+"|"+caravanX+"|"+caravanZ+"|"+selectedGood()+"|16");},ok&&operations!=null&&operations.warehouse()[goodIndex]>=16);
                            button("Caravan 32",sx+half+5,y,half,"Escort 32 resources. Rival patrols may intercept",()->{if(selected!=null)operationCommand("sendcaravan",selected+"|"+caravanX+"|"+caravanZ+"|"+selectedGood()+"|32");},ok&&operations!=null&&operations.warehouse()[goodIndex]>=32);
                        }
                        case 6->button("Raid Selected Caravan",sx,y,sw,"Send selected army to intercept visible rival shipment",()->caravanCommand("raidcaravan"),ok&&caravan!=null&&caravan.rival());
                        case 7->{
                            button("Center Site",sx,y,half,"Center map on selected site",()->{if(site!=null){viewX=site.x()/16.0;viewZ=site.z()/16.0;refresh();}},site!=null);
                            button("Refresh",sx+half+5,y,half,"Update mission progress and outpost alarms",this::refresh,true);
                        }
                        case 8->button("Center on HQ",sx,y,sw,"Move map back to this War Table",()->{viewX=data.table().getX()/16.0;viewZ=data.table().getZ()/16.0;refresh();},true);
                        case 9->{
                            button("Deposit 16",sx,y,half,"Deposit 16 selected goods from your inventory to treasury/warehouse",()->operationCommand("deposit",selectedGood()+"|16"),data.authorized());
                            button("Deposit 64",sx+half+5,y,half,"Deposit 64 selected goods from your inventory to treasury/warehouse",()->operationCommand("deposit",selectedGood()+"|64"),data.authorized());
                        }
                        case 10->button("Military Orders",sx,y,sw,"Return to army formation and march controls",()->{missions=false;actionScroll=0;clearWidgets();init();},true);
                    }
                    continue;
                }
                switch(row){
                    case 0->{button("Hold",sx,y,half,"Hold this location and defend nearby",()->commandSelected("hold"),ok);button("Follow",sx+half+5,y,half,"Follow your player; stops safely if you disconnect",()->commandSelected("follow"),ok);}
                    case 1->{button("Retreat",sx,y,half,"Return to headquarters; ignore combat while retreating",()->commandSelected("home"),ok);button("Center squad",sx+half+5,y,half,"Center map on selected squad",()->{if(a!=null){viewX=a.x()/16.0;viewZ=a.z()/16.0;refresh();}},ok);}
                    case 2->{button("Recruit +10",sx,y,half,"30 iron + 180 wheat; requires nearby registered barracks",()->commandSelected("recruit10"),ok);button("Recruit +50",sx+half+5,y,half,"150 iron + 900 wheat; directly adds troops to squad",()->commandSelected("recruit50"),ok);}
                    case 3->{button("Reserve +10",sx,y,half,"Use existing reserve troops at this control point",()->commandSelected("assign10"),ok&&data.reserves()>=10);button("Reserve +50",sx+half+5,y,half,"Use 50 existing reserve troops",()->commandSelected("assign50"),ok&&data.reserves()>=50);}
                    case 4->button("Formation: "+(a==null?"—":a.formation()),sx,y,sw,"Cycle column, line, and wedge",()->{if(a!=null)commandSelected("formation",a.formation().equals("column")?"line":a.formation().equals("line")?"wedge":"column");},ok);
                    case 5->button("Uniform: "+(a==null?"—":UNIFORMS[Math.max(0,Math.min(a.uniform(),3))]),sx,y,sw,"Cycle squad uniform",()->{if(a!=null)commandSelected("uniform",String.valueOf((a.uniform()+1)%4));},ok);
                    case 6->button("Split squad in half",sx,y,sw,"Hold and gather physical soldiers first",()->commandSelected("split"),ok);
                    case 7->{button("Return 10",sx,y,half,"Return troops to reserve at headquarters",()->commandSelected("return10"),ok);button("Return 50",sx+half+5,y,half,"Return troops to reserve at headquarters",()->commandSelected("return50"),ok);}
                    case 8->button("Gather soldiers",sx,y,sw,"Return all loaded surviving soldiers to the squad ledger while holding",()->commandSelected("gather"),ok);
                    case 9->button("Disband empty squad",sx,y,sw,"Requires no assigned or deployed troops",()->commandSelected("disband"),ok&&a.strength()+a.fielded()==0);
                }
            }
        }
        private static int col(short value){
            int v=value&65535;if(v==WarMapData.UNKNOWN)return 0xFF222B2C;
            int red=((v>>8)&15)*17,green=((v>>4)&15)*17,blue=(v&15)*17;
            return 0xFF000000|(red<<16)|(green<<8)|blue;
        }
        private void drawMap(GuiGraphics g){
            int left=mapLeft(),top=mapTop(),right=mapRight(),bottom=mapBottom();
            MinecraftMenu.inset(g,left-2,top-2,right+2,bottom+2);
            g.fill(left,top,right,bottom,0xFF1E2828);
            g.enableScissor(left,top,right,bottom);
            int n=WarMapData.SIZE,sub=WarMapData.SUB;
            // Each sampled pixel represents a 4x4 block surface, instead of one 16x16 chunk.
            for(int dz=0;dz<n;dz++)for(int dx=0;dx<n;dx++){
                int cx=data.cx()+dx-n/2,cz=data.cz()+dz-n/2;
                double x=midX()+(cx-viewX)*zoom,y=midY()+(cz-viewZ)*zoom;
                if(x+zoom<left||x>right||y+zoom<top||y>bottom)continue;
                int offset=(dz*n+dx)*sub*sub;
                for(int py=0;py<sub;py++)for(int px=0;px<sub;px++){
                    int x0=(int)Math.floor(x+zoom*px/sub),y0=(int)Math.floor(y+zoom*py/sub);
                    int x1=(int)Math.ceil(x+zoom*(px+1)/sub),y1=(int)Math.ceil(y+zoom*(py+1)/sub);
                    if(x0<right&&x1>left&&y0<bottom&&y1>top)
                        g.fill(x0,y0,Math.max(x0+1,x1),Math.max(y0+1,y1),col(data.terrain()[offset+py*sub+px]));
                }
                byte claim=data.claims().length==n*n?data.claims()[dz*n+dx]:0;
                if(showClaims&&claim!=0){
                    int tint=claim==1?0x4542C562:claim==2?0x454F97DD:claim==3?0x45E0524F:0x45E79F4A;
                    int xx=(int)x,yy=(int)y,rr=(int)Math.ceil(x+zoom),bb=(int)Math.ceil(y+zoom);
                    g.fill(xx,yy,rr,bb,tint);
                    if(zoom>=5){int edge=claim==1?0xFF60D98B:claim==2?0xFF68AFFC:claim==3?0xFFED6B6B:0xFFE8AD57;
                        g.fill(xx,yy,rr,yy+1,edge);
                    }
                }
            }
            int hx=(int)Math.round(screenX(data.table().getX())),hy=(int)Math.round(screenY(data.table().getZ()));
            g.fill(hx-4,hy-4,hx+5,hy+5,0xFF101010);g.fill(hx-2,hy-2,hx+3,hy+3,0xFFFFFFFF);
            for(var a:data.armies()){
                int x=(int)Math.round(screenX(a.x())),y=(int)Math.round(screenY(a.z()));
                if(a.marching()){
                    int tx=(int)Math.round(screenX(a.rallyX())),ty=(int)Math.round(screenY(a.rallyZ()));
                    for(int i=0;i<20;i++){int qx=x+(tx-x)*i/20,qy=y+(ty-y)*i/20;g.fill(qx,qy,qx+2,qy+2,0xFFE6D29D);}
                    g.fill(tx-4,ty-1,tx+5,ty+2,0xFFFFDA76);g.fill(tx-1,ty-4,tx+2,ty+5,0xFFFFDA76);
                }
                boolean active=a.id().equals(selected);int c=active?0xFFF4D56E:0xFF9DCEDB;
                g.fill(x-5,y-5,x+6,y+6,0xFF121B1D);g.fill(x-3,y-3,x+4,y+4,c);
                if(insideMap(x,y))g.drawString(font,clip(a.name()+" ["+(a.strength()+a.fielded())+"]",100),x+9,y-5,0xFFFFFFFF,true);
            }
            if(operations!=null){
                for(var site:operations.sites()){
                    int px=(int)screenX(site.x()),py=(int)screenY(site.z());
                    if(!insideMap(px,py))continue;
                    int color=site.rival()?0xFFD36F66:site.friendly()?0xFF7FD69C:0xFFE4C584;
                    // Pixel-art icons: mine arch, trees, wheat, quarry stone and loot chest.
                    g.fill(px-6,py-6,px+7,py+6,0xFF242420);
                    if(site.type().equals("MINE")){g.fill(px-5,py-4,px+6,py-2,color);g.fill(px-5,py-3,px-2,py+5,color);g.fill(px+3,py-3,px+6,py+5,color);}
                    else if(site.type().equals("FOREST")){g.fill(px-2,py+1,px+2,py+5,0xFF8C5A35);g.fill(px-5,py-5,px+6,py+2,color);}
                    else if(site.type().equals("FARM")){g.fill(px-1,py-5,px+2,py+5,0xFFCEAB52);g.fill(px-5,py-3,px+6,py,color);}
                    else if(site.type().equals("QUARRY")){g.fill(px-5,py+1,px+6,py+5,color);g.fill(px-2,py-4,px+3,py+1,color);}
                    else {g.fill(px-5,py-1,px+6,py+5,color);g.fill(px-3,py-5,px+4,py,color);}
                    if(site.stage().equals("OUTPOST")){g.fill(px-1,py-8,px+2,py-4,0xFFFFFFFF);}
                    if(site.alert())g.fill(px+4,py-8,px+7,py-5,0xFFFF3434);
                    if(site.id().equals(selectedSite())){g.fill(px-9,py-9,px+10,py-8,0xFFFFFF89);g.fill(px-9,py+8,px+10,py+9,0xFFFFFF89);}
                    if(zoom>=10)g.drawString(font,clip(site.type()+" "+site.progress()+"%",75),px+9,py-5,0xFFFFFFFF,true);
                }
                for(var caravan:operations.caravans()){
                    int px=(int)screenX(caravan.x()),py=(int)screenY(caravan.z());if(!insideMap(px,py))continue;
                    g.fill(px-6,py-3,px+7,py+4,caravan.rival()?0xFFE87770:0xFFDBAF63);g.fill(px-4,py+4,px-1,py+6,0xFF111111);g.fill(px+2,py+4,px+5,py+6,0xFF111111);
                }
            }
            g.disableScissor();
        }
        private UUID selectedSite(){return selectedSite;}
        private void outline(GuiGraphics g,int x,int y,int w,int h,int color){
            g.fill(x-2,y-2,x+w+2,y,color);g.fill(x-2,y+h,x+w+2,y+h+2,color);
            g.fill(x-2,y,x,y+h,color);g.fill(x+w,y,x+w+2,y+h,color);
        }
        private void drawSidebar(GuiGraphics g){
            int sx=sideX(),inner=sx+7,sw=width-inner-8;
            MinecraftMenu.panel(g,sx,0,width,height);
            g.drawString(font,treasury?"LOCAL WAREHOUSE":missions?"KINGDOM OPERATIONS":"SQUAD COMMAND",inner,10,0xFF404040,false);
            g.drawString(font,clip(data.faction()+" ("+data.type()+")",sw-20),inner,26,0xFF404040,false);
            g.drawString(font,"Reserve "+data.reserves()+"  /  Assigned "+data.assigned(),inner,38,0xFF404040,false);
            if(treasury){
                g.drawString(font,"NEARBY WAREHOUSE STOCK",inner,98,0xFF404040,false);
                if(operations==null){g.drawString(font,"Fetching warehouse balances...",inner,112,0xFF404040,false);return;}
                int[] supplies=operations.warehouse();
                g.drawString(font,"Iron:  "+supplies[0],inner,112,0xFF404040,false);
                g.drawString(font,"Wheat: "+supplies[3],inner+sw/2,112,0xFF404040,false);
                int infoY=Math.max(290,Math.min(height-155,325));
                if(infoY+122<height){
                    MinecraftMenu.inset(g,inner,infoY,width-8,infoY+123);
                    g.drawString(font,"FIVE-MINUTE UPKEEP",inner+6,infoY+8,0xFF404040,false);
                    g.drawString(font,"Costs: "+operations.ironDue()+" iron + "+operations.wheatDue()+" wheat",inner+6,infoY+22,0xFF404040,false);
                    g.drawString(font,"Settlement size: "+operations.population(),inner+6,infoY+36,0xFF404040,false);
                    int left=Math.max(0,operations.secondsLeft());
                    g.drawString(font,(operations.funded()?"Next charge: ":"Awaiting payment: ")+(left/60)+":"+String.format("%02d",left%60),inner+6,infoY+49,0xFF404040,false);
                    g.drawString(font,operations.funded()?"Last charge: PAID / INITIAL":"Last charge: UNPAID",inner+6,infoY+63,operations.funded()?0xFF246B26:0xFFA01919,false);
                    g.drawString(font,operations.canPayNext()?"Next charge: COVERED":"Next charge: INSUFFICIENT",inner+6,infoY+77,operations.canPayNext()?0xFF246B26:0xFFA01919,false);
                    g.drawString(font,"Successful charges: "+operations.paidCycles(),inner+6,infoY+91,0xFF404040,false);
                    g.drawString(font,clip(operations.upkeepStatus(),sw-16),inner+6,infoY+104,operations.funded()?0xFF246B26:0xFFA01919,false);
                }
                if(!data.message().isBlank()){
                    int my=Math.min(height-43,infoY+136);
                    g.drawString(font,clip(data.message(),sw-8),inner,my,0xFF404040,false);
                    if(data.message().length()>35)g.drawString(font,clip(data.message().substring(Math.min(35,data.message().length())),sw-8),inner,my+12,0xFF404040,false);
                }
                return;
            }
            g.drawString(font,"SQUADS   "+data.armies().size()+" / 15",inner,97,0xFF404040,false);
            if(data.armies().isEmpty()){
                g.drawString(font,"Create a squad above to begin",inner+4,rosterTop()+6,0xFF404040,false);
            }
            var a=army();
            int summaryY=summaryTop();
            MinecraftMenu.inset(g,inner,summaryY,width-8,summaryY+summaryHeight());
            if(missions){
                var site=site();var caravan=caravan();
                String line1=site!=null?site.type()+"  "+site.stage():caravan!=null?"CARAVAN  "+caravan.good():"Click a resource icon";
                String line2=site!=null?site.progress()+"% | Stock "+site.stock()+(site.scouted()?" / "+site.total():" / ?"):caravan!=null?caravan.amount()+" cargo":"Select army above and site on map";
                String line3=site!=null?(site.scouted()?"Duration: "+site.duration()+"s":"Scout to reveal exact yield"):"Destination: "+caravanX+", "+caravanZ;
                g.drawString(font,clip(line1,sw-12),inner+5,summaryY+5,0xFF404040,false);
                if(summaryHeight()>30)g.drawString(font,clip(line2,sw-12),inner+5,summaryY+16,0xFF404040,false);
                if(summaryHeight()>38)g.drawString(font,clip(line3,sw-12),inner+5,summaryY+27,0xFF404040,false);
            }else if(a!=null){
                g.drawString(font,clip(a.name()+(summaryHeight()<30?" / "+(a.strength()+a.fielded())+" troops":""),sw-12),inner+5,summaryY+5,0xFF404040,false);
                if(summaryHeight()>30)g.drawString(font,clip((a.strength()+a.fielded())+" troops / "+a.fielded()+" represented",sw-12),inner+5,summaryY+16,0xFF404040,false);
                if(summaryHeight()>38)g.drawString(font,clip("X "+a.x()+"  Z "+a.z()+"  "+a.order().toUpperCase(),sw-12),inner+5,summaryY+27,0xFF404040,false);
            }else g.drawString(font,"Select or create an army",inner+5,summaryY+12,0xFF404040,false);
            g.drawString(font,missions?"MISSIONS / CARAVANS (scroll)":"ORDERS (scroll to see more)",inner,ordersLabelY(),0xFF404040,false);
            int visible=visibleOrderRows();
            if(missions&&operations!=null){
                int secs=operations.secondsLeft();String upkeep="5m upkeep "+operations.ironDue()+" iron / "+operations.wheatDue()+" wheat  "+(secs/60)+":"+String.format("%02d",secs%60);
                g.drawString(font,clip(upkeep,sw-7),inner,height-29,operations.funded()?0xFF246B26:0xFFA01919,false);
            }else g.drawString(font,"Orders "+(visible==0?0:actionScroll+1)+"-"+Math.min(10,actionScroll+visible)+" / 10",inner,height-29,0xFF404040,false);
            g.drawString(font,missions?(operations!=null&&!operations.funded()?"UNFUNDED: operations paused":"Click sites or caravan targets"):"Left select / Right click move or attack",inner,height-17,0xFF404040,false);
        }
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            renderBackground(g);MinecraftMenu.panel(g,0,0,width,height);
            drawMap(g);drawSidebar(g);
            g.drawString(font,"WORLD MAP  •  "+clip(data.faction(),95),mapLeft()+3,3,0xFF404040,false);
            g.drawString(font,"Left: select | Right: order / drag pan | Wheel: zoom",mapLeft()+7,height-34,0xFF404040,false);
            g.drawString(font,"Green kingdom / Blue town / Red other / Dark unexplored",mapLeft()+7,height-21,0xFF404040,false);
            if(insideMap(mx,my)){
                int x=(int)Math.floor((viewX+(mx-midX())/zoom)*16.0),z=(int)Math.floor((viewZ+(my-midY())/zoom)*16.0);
                g.drawString(font,"X: "+x+"  Z: "+z,mapLeft()+7,mapTop()+6,0xFFFFFFFF,true);
            }
            String message=!data.message().isBlank()?data.message():localMessage;
            if(!message.isBlank())g.drawCenteredString(font,clip(message,Math.max(80,mapRight()-20)),(mapLeft()+mapRight())/2,height-9,0xFFFFFFFF);
            super.render(g,mx,my,partial);
            // Bright white selection outlines, independent of Minecraft button hover state.
            int sx=sideX()+7,sw=width-sx-8,third=(sw-10)/3;
            int activeX=treasury?sx+2*(third+5):missions?sx+third+5:sx;
            int activeW=treasury?sw-2*(third+5):third;
            outline(g,activeX,69,activeW,20,0xFFFFFFFF);
            if(!treasury)for(int i=rosterScroll;i<Math.min(data.armies().size(),rosterScroll+rosterRows());i++){
                if(data.armies().get(i).id().equals(selected))outline(g,sx,rosterTop()+(i-rosterScroll)*23,sw,20,0xFFFFFFFF);
            }

        }
        @Override public boolean mouseClicked(double mx,double my,int b){
            // Widget hit-testing must happen first: buttons and text entry never issue map orders.
            if(super.mouseClicked(mx,my,b))return true;
            if(b==1&&insideMap(mx,my)){dragging=true;dragMoved=false;dragStartX=mx;dragStartY=my;return true;}
            if(b==2&&insideMap(mx,my)){dragging=true;dragMoved=true;return true;}
            if(b==0&&insideMap(mx,my)){
                if(operations!=null){
                    for(var site:operations.sites())if(Math.hypot(screenX(site.x())-mx,screenY(site.z())-my)<12){selectedSite=site.id();selectedCaravan=null;missions=true;treasury=false;localMessage="Selected "+site.type()+" site";clearWidgets();init();return true;}
                    for(var caravan:operations.caravans())if(Math.hypot(screenX(caravan.x())-mx,screenY(caravan.z())-my)<12){selectedCaravan=caravan.id();selectedSite=null;missions=true;treasury=false;localMessage="Selected "+caravan.good()+" caravan";clearWidgets();init();return true;}
                }
                if(missions||treasury){caravanX=(int)Math.floor((viewX+(mx-midX())/zoom)*16);caravanZ=(int)Math.floor((viewZ+(my-midY())/zoom)*16);localMessage="Caravan destination X "+caravanX+" Z "+caravanZ;return true;}
                for(var a:data.armies())if(Math.hypot(screenX(a.x())-mx,screenY(a.z())-my)<9){selected=a.id();localMessage="Selected "+a.name();clearWidgets();init();return true;}
                localMessage="Right-click a destination to order the selected squad.";return true;
            }
            return false;
        }
        @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){
            if((button==1||button==2)&&dragging){if(button==2||Math.hypot(mx-dragStartX,my-dragStartY)>4)dragMoved=true;if(dragMoved){viewX-=dx/zoom;viewZ-=dy/zoom;}return true;}
            return super.mouseDragged(mx,my,button,dx,dy);
        }
        @Override public boolean mouseReleased(double mx,double my,int button){
            if((button==1||button==2)&&dragging){dragging=false;if(dragMoved){refresh();return true;}if(button==1&&!missions&&!treasury&&insideMap(mx,my)){var a=army();if(a==null)return true;int x=(int)Math.floor((viewX+(mx-midX())/zoom)*16),z=(int)Math.floor((viewZ+(my-midY())/zoom)*16);int dx=(x>>4)-data.cx()+WarMapData.SIZE/2,dz=(z>>4)-data.cz()+WarMapData.SIZE/2;boolean enemy=dx>=0&&dz>=0&&dx<WarMapData.SIZE&&dz<WarMapData.SIZE&&data.claims().length==WarMapData.SIZE*WarMapData.SIZE&&data.claims()[dz*WarMapData.SIZE+dx]>=3;commandSelected(enemy?"attack":"march",x+"|"+z);return true;}return true;}
            return super.mouseReleased(mx,my,button);
        }
        @Override public boolean mouseScrolled(double mx,double my,double delta){
            if(insideMap(mx,my)){zoom=Math.max(2,Math.min(42,zoom*(delta>0?1.24:1/1.24)));return true;}
            if(mx>=sideX()){
                if(treasury)return true;
                if(my>=rosterTop()&&my<summaryTop()){
                    int count=rosterRows();
                    rosterScroll=Math.max(0,Math.min(Math.max(0,data.armies().size()-count),rosterScroll+(delta<0?1:-1)));
                }else if(my>=ordersTop()){
                    actionScroll=Math.max(0,Math.min(maxActionScroll(),actionScroll+(delta<0?1:-1)));
                }
                clearWidgets();init();return true;
            }
            return super.mouseScrolled(mx,my,delta);
        }
        @Override public void tick(){if(++refreshTicks%60==0&&!(armyName!=null&&armyName.isFocused()))refresh();}
        @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE&&selected!=null){selected=null;clearWidgets();init();return true;}return super.keyPressed(key,scan,modifiers);}
        @Override public boolean isPauseScreen(){return false;}
    }
}
