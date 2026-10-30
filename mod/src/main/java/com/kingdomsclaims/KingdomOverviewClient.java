package com.kingdomsclaims;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.UUID;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraft.client.gui.components.EditBox;
import java.util.Set;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.lwjgl.glfw.GLFW;

/**
 * Kingdoms-and-Castles-inspired 3D planner using the real client world renderer.
 * This is only a visual camera: no teleport, chunk tickets, spectator mode,
 * invulnerability, server movement or revealing undiscovered map terrain.
 * Military and district changes continue to be checked on the server.
 */
public final class KingdomOverviewClient {
    private KingdomOverviewClient() {}
    private static DistrictNetwork.Snapshot latest;

    /** The GameRenderer draws a first-person item even for a synthetic camera entity.
     * Cancel that separate render path while the Planner is active. */
    @Mod.EventBusSubscriber(modid = KingdomsClaims.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class PlannerHandHook {
        private PlannerHandHook() {}
        @SubscribeEvent public static void hideFirstPersonHand(RenderHandEvent event) {
            if (Minecraft.getInstance().screen instanceof OverviewScreen) event.setCanceled(true);
        }
        /** Update the synthetic camera BEFORE world rendering, not inside the GUI render pass.
         * The 0.4.4 post-world update introduced visible one-frame position/angle mismatches. */
        /** Render district borders over the actual terrain; only nearby loaded chunks. */
        @SubscribeEvent public static void renderDistrictsInWorld(RenderLevelStageEvent event) {
            if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_PARTICLES
                    && Minecraft.getInstance().screen instanceof OverviewScreen screen) {
                screen.renderOwnedClaims(event);
            }
        }
        @SubscribeEvent public static void beforeWorldRender(TickEvent.RenderTickEvent event) {
            if (event.phase==TickEvent.Phase.START
                    && Minecraft.getInstance().screen instanceof OverviewScreen screen) {
                screen.advanceCameraForFrame();
            }
        }
    }


    /** First interaction opens 3D; editor response packets refresh whichever editor is open. */
    public static void accept(DistrictNetwork.Snapshot data) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null)return;
        latest=data;
        if(DistrictClient.isOpen()) { DistrictClient.accept(data); return; }
        if(mc.screen instanceof OverviewScreen screen && screen.data.manager().equals(data.manager())) {
            screen.data=data;
            screen.alert=data.message();
            if(data.message().startsWith("Claimed "))screen.selectedClaimChunk=null;
            if(data.message().startsWith("Created ")||data.message().startsWith("Painted ")||data.message().startsWith("Removed "))screen.brushedChunks.clear();
            if(screen.selectedZoneId!=null&&data.districts().stream().noneMatch(z->z.id().equals(screen.selectedZoneId)))screen.selectedZoneId=null;
            if(screen.targetZone>=data.districts().size())screen.targetZone=-1;
            screen.refreshWidgets();
            return;
        }
        open(data);
    }
    public static void acceptBlueprints(BlueprintNetwork.Panel panel) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.screen instanceof OverviewScreen screen && screen.data.manager().equals(panel.manager())) {
            UUID selected=screen.selectedBlueprintId();
            screen.blueprints=panel.plans();
            screen.blueprintFolders=panel.folders();
            screen.activeSites=panel.active();
            screen.blueprintIndex=0;
            screen.folderFilterIndex=Math.min(screen.folderFilterIndex,screen.blueprintFolders.size());
            var visible=screen.visibleBlueprints();
            for(int i=0;i<visible.size();i++)if(visible.get(i).id().equals(selected))screen.blueprintIndex=i;
            screen.moveFolderIndex=Math.min(screen.moveFolderIndex,Math.max(0,screen.blueprintFolders.size()-1));
            if(!panel.message().isBlank())screen.alert=panel.message();
            screen.refreshWidgets();
        }
    }
    public static void open(DistrictNetwork.Snapshot data) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null)return;
        latest=data;
        mc.setScreen(new OverviewScreen(data));
    }
    /** The old two-dimensional map can return without a second, unguarded server request. */
    public static void returnFromWarMap(BlockPos manager) {
        Minecraft mc=Minecraft.getInstance();
        if(latest!=null && latest.manager().equals(manager) && mc.level!=null && mc.player!=null
                && mc.level.getBlockState(manager).is(KingdomsClaims.KINGDOM_MANAGER.get())
                && mc.player.distanceToSqr(manager.getX()+.5,manager.getY()+.5,manager.getZ()+.5)<=64)
            open(latest);
        else if(mc.player!=null)mc.setScreen(null);
    }

    private static final class OverviewScreen extends Screen {
        private DistrictNetwork.Snapshot data;
        private Entity camera;
        private CameraType originalView;
        private boolean originalHideGui;
        private Entity originalCamera;
        private final Set<Integer> keysHeld = new HashSet<>();
        private long lastRenderNanos=0;
        private boolean blockedByUnloadedChunk=false;
        private enum Pane { NONE, CLAIMS, ZONING, DISTRICTS, ECONOMY, ARCHITECT }
        private Pane pane=Pane.NONE;
        private boolean financePage=false;
        private int chosenResource=0;
        private static final String[] RESOURCE_LABELS={"Iron", "Coal", "Logs", "Wheat", "Stone", "Emeralds", "Gold", "Gold Nuggets"};
        private boolean zoningMode=false,painting=false;
        private boolean architectMode=false;
        private UUID selectedZoneId=null;
        private UUID deleteConfirmId=null;
        private int districtScroll=0;
        private boolean showHelp=false;
        private boolean showClaims=true;
        private Long selectedClaimChunk=null;
        private boolean claimTown=false;
        private java.util.List<BlueprintNetwork.Summary> blueprints=java.util.List.of();
        private int blueprintIndex=0,blueprintTurns=0,activeSites=0;
        private java.util.List<String> blueprintFolders=java.util.List.of("Houses","Roads","Farms","Workshops","Defenses","Decor","Other");
        private int folderFilterIndex=1,moveFolderIndex=0,folderScroll=0,buildScroll=0;
        private EditBox newFolderName;
        private BlockPos blueprintSite=null;
        private String filteredFolder(){return folderFilterIndex==0?"All builds":blueprintFolders.get(Math.min(folderFilterIndex-1,blueprintFolders.size()-1));}
        private java.util.List<BlueprintNetwork.Summary> visibleBlueprints(){
            if(folderFilterIndex==0)return blueprints;
            String folder=filteredFolder();return blueprints.stream().filter(p->p.folder().equalsIgnoreCase(folder)).toList();
        }
        private BlueprintNetwork.Summary selectedBlueprint(){
            var visible=visibleBlueprints();return visible.isEmpty()?null:visible.get(Math.floorMod(blueprintIndex,visible.size()));
        }
        private UUID selectedBlueprintId(){var b=selectedBlueprint();return b==null?new UUID(0,0):b.id();}
        private void toggleArchitect(){
            setPane(pane==Pane.ARCHITECT?Pane.NONE:Pane.ARCHITECT);
            if(architectMode)BlueprintNetwork.CHANNEL.sendToServer(new BlueprintNetwork.Request(data.manager()));
        }
        private void setPane(Pane next){
            if(next==Pane.CLAIMS&&pane!=Pane.CLAIMS)claimTown="town".equals(data.factionType());
            pane=next;zoningMode=next==Pane.ZONING;architectMode=next==Pane.ARCHITECT;
            painting=false;brushedChunks.clear();blueprintSite=null;deleteConfirmId=null;
            if(next!=Pane.CLAIMS)selectedClaimChunk=null;
            refreshWidgets();
        }
        private void chooseDistrict(UUID id){
            selectedZoneId=id;deleteConfirmId=null;refreshWidgets();
        }
        private int selectedDistrictIndex(){
            for(int i=0;i<data.districts().size();i++)if(data.districts().get(i).id().equals(selectedZoneId))return i;
            return -1;
        }
        private DistrictNetwork.ZoneInfo selectedDistrict(){
            int i=selectedDistrictIndex();return i<0?null:data.districts().get(i);
        }
        private void editSelectedLand(){
            int i=selectedDistrictIndex();if(i<0){alert="Choose a district first.";return;}
            zoneType=data.districts().get(i).type();setPane(Pane.ZONING);
            targetZone=i;zoneName=null;refreshWidgets();
        }
        private void submitDistrict(String task){
            var z=selectedDistrict();
            if(z==null){alert="Choose a district first.";return;}
            if(task.equals("delete")&&!z.id().equals(deleteConfirmId)){
                deleteConfirmId=z.id();alert="Press CONFIRM DELETE again to remove "+z.name()+".";refreshWidgets();return;
            }
            DistrictNetwork.CHANNEL.sendToServer(new DistrictNetwork.Action(data.manager(),task,z.id(),z.name(),z.type(),new long[0],data.cx(),data.cz()));
            deleteConfirmId=null;alert="Updating district...";
        }
        private void economyAction(String task,int option){
            DistrictNetwork.CHANNEL.sendToServer(new DistrictNetwork.Action(data.manager(),task,new UUID(0,0),"",option,new long[0],data.cx(),data.cz()));
            alert="Updating stockpile...";
        }
        private void resourceAction(String operation){economyAction(operation,chosenResource);}
        private void refreshData(){
            DistrictNetwork.CHANNEL.sendToServer(new DistrictNetwork.Request(data.manager(),data.cx(),data.cz()));
            alert="Refreshing kingdom data...";
        }
        private void cycleBlueprint(){
            var visible=visibleBlueprints();
            if(!visible.isEmpty())blueprintIndex=(blueprintIndex+1)%visible.size();
            blueprintSite=null;refreshWidgets();
        }
        private void nextFolder(){folderFilterIndex=(folderFilterIndex+1)%(blueprintFolders.size()+1);blueprintIndex=0;buildScroll=0;}
        private void nextMoveFolder(){if(!blueprintFolders.isEmpty())moveFolderIndex=(moveFolderIndex+1)%blueprintFolders.size();refreshWidgets();}
        private void moveBlueprint(){
            var plan=selectedBlueprint();
            if(plan==null||blueprintFolders.isEmpty()){alert="Choose a saved design first.";return;}
            BlueprintNetwork.CHANNEL.sendToServer(new BlueprintNetwork.CatalogAction(data.manager(),"move",plan.id(),blueprintFolders.get(moveFolderIndex)));
            alert="Organizing design...";
        }
        private void createFolder(){
            if(newFolderName==null||newFolderName.getValue().strip().isEmpty()){alert="Enter a folder name first.";return;}
            BlueprintNetwork.CHANNEL.sendToServer(new BlueprintNetwork.CatalogAction(data.manager(),"newfolder",new UUID(0,0),newFolderName.getValue().strip()));
            alert="Creating folder...";
        }
        private void startConstruction(){
            var plan=selectedBlueprint();
            if(plan==null){alert="Capture your first house with /architect select.";return;}
            BlueprintNetwork.CHANNEL.sendToServer(new BlueprintNetwork.Prepare(data.manager(),plan.id()));
            // Return to your actual character so the preview follows the crosshair.
            onClose();
        }
        private final LinkedHashSet<Long> brushedChunks = new LinkedHashSet<>();
        private int zoneType=0, targetZone=-1;
        private EditBox zoneName;
        private String alert="";
        private double lastMouseX,lastMouseY;
        private BlockPos hoverBlock=null;
        private static final int[] ZONE_COLORS={0xFF62BBF1,0xFFFFCA6A,0xFFE99C85,0xFF95D77A};
        // These are original HUD colors; no Kingdoms and Castles artwork is bundled.
        private static final int HUD_PANEL=0xE41A2930, HUD_BORDER=0xFF72847D;
        private static final int HUD_GOLD=0xFFE8C57C, HUD_TEXT=0xFFECE5D3;

        /** Dark wood/stone styled strategy buttons rather than vanilla gray GUI buttons. */
        private static final class StrategyButton extends Button {
            private final int accent;
            StrategyButton(String label,int bx,int by,int bw,int bh,int accent,Runnable click) {
                super(bx,by,bw,bh,Component.literal(label),b->click.run(),DEFAULT_NARRATION);
                this.accent=accent;
            }
            @Override protected void renderWidget(GuiGraphics g,int mouseX,int mouseY,float partialTick) {
                int bx=getX(),by=getY(),bw=getWidth(),bh=getHeight();
                boolean hot=isHoveredOrFocused();
                g.fill(bx,by,bx+bw,by+bh,hot?0xF0445555:0xEB23313A);
                g.fill(bx,by,bx+bw,by+2,hot?0xFFF6D995:accent);
                g.fill(bx,by+bh-1,bx+bw,by+bh,0xFF718079);
                g.fill(bx,by,bx+1,by+bh,0xFF627773);
                g.fill(bx+bw-1,by,bx+bw,by+bh,0xFF627773);
                String text=getMessage().getString();
                int maxWidth=Math.max(10,bw-8);
                while(text.length()>1 && Minecraft.getInstance().font.width(text)>maxWidth)
                    text=text.substring(0,text.length()-2)+"…";
                g.drawCenteredString(Minecraft.getInstance().font,text,bx+bw/2,by+(bh-8)/2,
                    active?(hot?0xFFFFFFFF:0xFFE9E2CF):0xFF7E8A89);
            }
        }


        private final double originX,originZ,originY;
        private double x,y,z;
        private float yaw=0,pitch=65;
        private boolean active=false;
        // Camera is deliberately limited to nearby, already-loaded terrain.
        private static final double LIMIT=128;
        private OverviewScreen(DistrictNetwork.Snapshot data) {
            super(Component.literal("Kingdom Planner — City View"));
            this.data=data;
            BlockPos pos=data.manager();
            originX=pos.getX()+.5;originY=pos.getY();originZ=pos.getZ()+.5;
            x=originX;y=originY+43;z=originZ;
        }
        private void btn(String label,int px,int py,int width,String hint,Runnable click) {
            btn(label,px,py,width,22,HUD_BORDER,hint,click);
        }
        private void btn(String label,int px,int py,int width,int bh,int accent,String hint,Runnable click) {
            var b=new StrategyButton(label,px,py,width,bh,accent,click);
            b.setTooltip(Tooltip.create(Component.literal(hint)));
            addRenderableWidget(b);
        }
        private void selectZoneType(int type) {
            String prior=zoneName==null?"":zoneName.getValue();
            zoneType=type;
            targetZone=-1; // Type palette always starts a new district; expands keep their existing type.
            if(zoneName!=null)zoneName.setValue(prior.isBlank()||prior.endsWith(" Quarter")
                ?DistrictData.TYPES[type]+" Quarter":prior);
            refreshWidgets();
        }
        @Override protected void init() {
            Minecraft mc=Minecraft.getInstance();
            if(!active && mc.level!=null && mc.player!=null) {
                // A client-only, non-living camera; the server player stays at the Planner.
                camera=EntityType.MARKER.create(mc.level);
                if(camera!=null) {
                    originalView=mc.options.getCameraType();
                    originalHideGui=mc.options.hideGui;
                    originalCamera=mc.getCameraEntity();
                    mc.options.hideGui=true;
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                    camera.setNoGravity(true);
                    active=true;
                    lastRenderNanos=System.nanoTime();
                    updateCamera();
                    mc.setCameraEntity(camera);
                }
            }
            // A compact fixed sidebar: world/camera never changes when opening docked panels.
            int sideX=10,sideY=66,sideW=sidebarWidth(),bh=24,step=30;
            btn((pane==Pane.ARCHITECT?"• ":"")+"Buildings",sideX,sideY,sideW,bh,pane==Pane.ARCHITECT?0xFFB2A4DD:HUD_BORDER,
                "Save or place houses with the first-person Architect tool",this::toggleArchitect);
            btn((pane==Pane.ECONOMY?"• ":"")+"Resources",sideX,sideY+step,sideW,bh,pane==Pane.ECONOMY?0xFFC1D898:HUD_BORDER,
                "Open your kingdom stockpile and finances without leaving the 3D view",()->setPane(pane==Pane.ECONOMY?Pane.NONE:Pane.ECONOMY));
            btn((pane==Pane.CLAIMS?"• ":"")+"Claims",sideX,sideY+step*2,sideW,bh,pane==Pane.CLAIMS?0xFF79CBA7:HUD_BORDER,
                "Click a terrain chunk in 3D to preview a kingdom or town claim",()->setPane(pane==Pane.CLAIMS?Pane.NONE:Pane.CLAIMS));
            btn("Exit planner",sideX,sideY+step*3,sideW,bh,0xFFA6ADA9,
                "Return to your character",this::onClose);
            if(pane==Pane.NONE){zoneName=null;return;}
            int px=panelX(), pw=panelWidth(), inner=px+12, w=pw-24;
            if(pane==Pane.CLAIMS){
                btn("Kingdom land",inner,116,(w-6)/2,22,!claimTown?0xFF87C8A4:HUD_BORDER,
                    "Expand the kingdom using the ordinary claim rules",()->{claimTown=false;refreshWidgets();});
                btn("Town land",inner+(w+6)/2,116,(w-6)/2,22,claimTown?0xFF87C8A4:HUD_BORDER,
                    "Expand a town inside its banner boundary",()->{claimTown=true;refreshWidgets();});
                btn("CLAIM SELECTED CHUNK",inner,194,w,26,0xFF87C8A4,
                    "Server checks adjacency, permission, distance and the exact material cost",this::claimSelected);
                btn("Refresh claims",inner,230,w,22,HUD_BORDER,
                    "Refresh your ownership and newly purchased land",this::refreshData);
                btn("Hide/show borders",inner,259,w,22,HUD_BORDER,
                    "Toggle your kingdom borders",()->{showClaims=!showClaims;refreshWidgets();});
            }
            if(pane==Pane.ARCHITECT){
                // Folder entries and their flyout are drawn on top of the 3D world by
                // renderBuildLibrary. Hover over a folder, then click a design.
                int y=height-178;
                btn("Register existing building",inner,y-25,w,21,0xFF9ED09A,"Select a manually built structure",()->{onClose();WorldSelectionClient.registerBuilding();});
                btn("Capture new build",inner,y,w,22,0xFF9BC5E2,
                    "Select a house, road, bridge or other build in player view",()->{
                        BlueprintNetwork.CHANNEL.sendToServer(new BlueprintNetwork.SelectRequest(data.manager()));onClose();
                    });
                btn("Place selected design",inner,y+27,w,22,0xFF9ED09A,
                    "Place the selected blueprint as a hologram; repeat placement is supported",this::startConstruction);
                btn(blueprintFolders.isEmpty()?"No folders":"Move to: "+blueprintFolders.get(Math.min(moveFolderIndex,blueprintFolders.size()-1))+"  ›",
                    inner,y+54,w,20,HUD_BORDER,"Choose the destination folder",this::nextMoveFolder);
                btn("Move selected design",inner,y+77,w,20,0xFFD2B984,
                    "Right-click a design in the flyout to select it, then move it",this::moveBlueprint);
                newFolderName=new EditBox(font,inner,y+101,w,19,Component.literal("New folder"));
                newFolderName.setMaxLength(22);
                newFolderName.setHint(Component.literal("New folder name"));
                addRenderableWidget(newFolderName);
                btn("Create folder",inner,y+125,w,21,0xFF9BC5E2,
                    "Add a folder for organizing saved builds",this::createFolder);
            }else newFolderName=null;
            if(pane==Pane.ZONING){
                // Zone controls stay in the right dock, leaving the center world free for chunk painting.
                int half=(w-6)/2;
                for(int i=0;i<4;i++){
                    final int type=i;int bx=inner+(i%2)*(half+6),by=116+(i/2)*28;
                    btn(DistrictData.TYPES[i],bx,by,half,22,targetZone<0&&zoneType==i?HUD_GOLD:ZONE_COLORS[i],
                        "Paint "+DistrictData.TYPES[i].toLowerCase()+" chunks",()->selectZoneType(type));
                }
                zoneName=new EditBox(font,inner,198,w,20,Component.literal("District name"));
                zoneName.setMaxLength(28);
                zoneName.setValue(targetZone>=0&&targetZone<data.districts().size()?data.districts().get(targetZone).name():DistrictData.TYPES[zoneType]+" Quarter");
                addRenderableWidget(zoneName);
                String target=targetZone>=0&&targetZone<data.districts().size()?"Expand: "+data.districts().get(targetZone).name():"Create new district";
                btn(target,inner,226,w,22,0xFF8BA6A4,
                    "Cycle new or existing districts. Paint only within the selected district.",()->{
                        targetZone++;if(targetZone>=data.districts().size())targetZone=-1;
                        if(targetZone>=0)zoneType=data.districts().get(targetZone).type();
                        zoneName=null;refreshWidgets();});
                btn(targetZone<0?"Create district":"Add painted land",inner,254,w,23,0xFF98D192,
                    "Save the selected 16×16 chunks",this::saveZone);
                btn("Clear selection",inner,284,w,21,0xFFD99C86,
                    "Clear the current brush without editing territory",()->{brushedChunks.clear();alert="Selection cleared.";});
            } else zoneName=null;
            if(pane==Pane.DISTRICTS){
                int visible=districtRowsVisible(),listTop=118;
                districtScroll=Math.max(0,Math.min(districtScroll,Math.max(0,data.districts().size()-visible)));
                for(int i=districtScroll;i<Math.min(data.districts().size(),districtScroll+visible);i++){
                    var info=data.districts().get(i);
                    String label=(info.id().equals(selectedZoneId)?"• ":"")+info.name()+"  L"+info.level();
                    btn(label,inner,listTop+(i-districtScroll)*24,w,21,info.id().equals(selectedZoneId)?HUD_GOLD:ZONE_COLORS[info.type()],
                        "Select "+info.name()+", "+info.chunks()+" claimed chunks",()->chooseDistrict(info.id()));
                }
                int scrollY=listTop+visible*24+4;
                btn("▲ Previous",inner,scrollY,(w-6)/2,20,HUD_BORDER,"Scroll district list upward",()->{districtScroll=Math.max(0,districtScroll-1);refreshWidgets();});
                btn("Next ▼",inner+(w+6)/2,scrollY,(w-6)/2,20,HUD_BORDER,"Scroll district list downward",()->{districtScroll++;refreshWidgets();});
                int actY=districtActionsY();
                btn("Paint / expand land",inner,actY,w,23,0xFF9BC5E2,
                    "Return to 3D chunk paint mode with this district selected",this::editSelectedLand);
                btn("Develop +1",inner,actY+29,w,22,0xFF98D192,
                    "Spend treasury resources to increase capacity and jobs",()->submitDistrict("upgrade"));
                btn(deleteConfirmId!=null&&deleteConfirmId.equals(selectedZoneId)?"CONFIRM DELETE":"Delete selected",inner,actY+57,w,21,
                    0xFFCE8E7A,"Delete the selected district permanently (requires two clicks)",()->submitDistrict("delete"));
            }
            if(pane==Pane.ECONOMY){
                int half=(w-6)/2;
                btn("Deposit gold nuggets",inner,145,w,22,HUD_GOLD,
                    "Deposit physical gold nuggets at your local warehouse",()->economyAction("gold",0));
                btn("Deposit gold ingots",inner,174,w,22,HUD_GOLD,
                    "Deposit physical gold ingots at your local warehouse",()->economyAction("gold",1));
                btn("Lower tax by 5%",inner,215,half,22,HUD_BORDER,
                    "Reduce the tax rate",()->economyAction("tax",Math.max(0,data.tax()-5)));
                btn("Raise tax by 5%",inner+half+6,215,half,22,HUD_BORDER,
                    "Increase the tax rate",()->economyAction("tax",Math.min(25,data.tax()+5)));
                btn("Refresh finances",inner,height-51,w,22,0xFFC1D898,
                    "Refresh current tax and settlement totals",this::refreshData);
            }
        }
        // Folder flyout: one compact tree instead of cycling through giant buttons.
        private static final int LIBRARY_TOP=120,LIBRARY_ROW=22;
        private int folderRowsVisible(){return Math.max(2,Math.min(11,(height-325)/LIBRARY_ROW));}
        private int planRowsVisible(){return Math.max(2,Math.min(10,(height-200)/26));}
        private int flyoutWidth(){return Math.min(235,Math.max(165,(width-sidebarWidth()-panelWidth()-45)));}
        private int flyoutX(){return Math.max(20+sidebarWidth(),panelX()-flyoutWidth()-7);}
        private int flyoutY(){return LIBRARY_TOP;}
        private boolean inFolderList(double mx,double my){
            return pane==Pane.ARCHITECT&&mx>=panelX()+10&&mx<panelX()+panelWidth()-10
                &&my>=LIBRARY_TOP&&my<LIBRARY_TOP+folderRowsVisible()*LIBRARY_ROW;
        }
        private boolean inDesignFlyout(double mx,double my){
            return pane==Pane.ARCHITECT&&mx>=flyoutX()&&mx<flyoutX()+flyoutWidth()
                &&my>=flyoutY()&&my<flyoutY()+36+planRowsVisible()*26;
        }
        private void renderBuildLibrary(GuiGraphics g,int mx,int my){
            if(pane!=Pane.ARCHITECT)return;
            int count=blueprintFolders.size()+1;
            int rows=folderRowsVisible();
            folderScroll=Math.max(0,Math.min(folderScroll,Math.max(0,count-rows)));
            // Changing hover filters the flyout, without rebuilding all widgets or the camera.
            if(inFolderList(mx,my)){
                int index=folderScroll+(my-LIBRARY_TOP)/LIBRARY_ROW;
                if(index>=0&&index<count&&index!=folderFilterIndex){
                    folderFilterIndex=index;blueprintIndex=0;buildScroll=0;
                }
            }
            int x=panelX()+11,w=panelWidth()-22;
            for(int i=0;i<rows&&folderScroll+i<count;i++){
                int index=folderScroll+i,y=LIBRARY_TOP+i*LIBRARY_ROW;
                String folder=index==0?"All builds":blueprintFolders.get(index-1);
                int n=index==0?blueprints.size():(int)blueprints.stream().filter(p->p.folder().equalsIgnoreCase(folder)).count();
                boolean hot=folderFilterIndex==index;
                g.fill(x,y,x+w,y+LIBRARY_ROW-2,hot?0xEE465A55:0xDD23313A);
                if(hot)g.fill(x,y,x+3,y+LIBRARY_ROW-2,HUD_GOLD);
                String title=font.plainSubstrByWidth("▸ "+folder,w-57);
                g.drawString(font,title,x+7,y+6,hot?0xFFFFE2AA:HUD_TEXT,false);
                g.drawString(font,n+"  ›",x+w-37,y+6,0xFFB3CBBC,false);
            }
            if(count>rows)g.drawString(font,"Scroll folders  ("+(folderScroll+1)+"–"+Math.min(count,folderScroll+rows)+" / "+count+")",x,LIBRARY_TOP+rows*LIBRARY_ROW+1,0xFFADC1B5,false);
            // Submenu opens adjacent to folders, towards the center of the 3D view.
            var plans=visibleBlueprints();int px=flyoutX(),pw=flyoutWidth(),py=flyoutY(),visible=planRowsVisible();
            buildScroll=Math.max(0,Math.min(buildScroll,Math.max(0,plans.size()-visible)));
            g.fill(px,py-5,px+pw,py+37+visible*26,0xF01A2930);
            g.fill(px,py-5,px+pw,py-3,HUD_GOLD);
            g.drawString(font,font.plainSubstrByWidth(filteredFolder(),pw-14),px+8,py+3,HUD_GOLD,false);
            g.drawString(font,"Click: place   Right-click: select",px+8,py+18,0xFFB6D3BE,false);
            if(plans.isEmpty())g.drawString(font,"No saved designs in this folder",px+8,py+43,HUD_TEXT,false);
            for(int i=0;i<visible&&i+buildScroll<plans.size();i++){
                int idx=i+buildScroll,yy=py+37+i*26;
                var plan=plans.get(idx);
                boolean hot=mx>=px+4&&mx<px+pw-4&&my>=yy&&my<yy+24;
                boolean selected=idx==blueprintIndex;
                g.fill(px+4,yy,px+pw-4,yy+24,hot?0xFF4D5F55:selected?0xFF374942:0xEA26363B);
                if(selected)g.fill(px+4,yy,px+7,yy+24,HUD_GOLD);
                g.drawString(font,font.plainSubstrByWidth(plan.name(),pw-22),px+11,yy+3,hot?0xFFFFE6A8:HUD_TEXT,false);
                g.drawString(font,""+plan.width()+"×"+plan.depth()+"  •  "+plan.blocks()+" blocks  •  "+plan.housing()+" residents",px+11,yy+14,0xFFB6D0BF,false);
            }
            if(plans.size()>visible)g.drawString(font,"Scroll for "+(plans.size()-visible)+" more designs",px+8,py+37+visible*26-11,HUD_GOLD,false);
        }
        private int sidebarWidth(){return Math.min(150,Math.max(112,width/6));}
        private int panelWidth(){return Math.min(286,Math.max(210,width/3));}
        private int panelX(){return Math.max(10+sidebarWidth()+10,width-panelWidth()-9);}
        private int districtRowsVisible(){return Math.max(1,Math.min(6,(height-330)/24));}
        private int districtActionsY(){return 118+districtRowsVisible()*24+39;}
        // Callbacks may execute while Screen.mouseClicked iterates its children.
        // Deferring widget replacement prevents concurrent modification of that list.
        private boolean widgetsDirty=false;
        private void refreshWidgets(){widgetsDirty=true;}
        private void applyDeferredWidgets(){
            if(!widgetsDirty)return;
            widgetsDirty=false;
            String nameText=zoneName==null?null:zoneName.getValue();
            String folderText=newFolderName==null?null:newFolderName.getValue();
            clearWidgets();init();
            if(zoneName!=null&&nameText!=null&&!nameText.isBlank())zoneName.setValue(nameText);
            if(newFolderName!=null&&folderText!=null&&!folderText.isBlank())newFolderName.setValue(folderText);
        }
        private void saveZone(){
            if(brushedChunks.isEmpty()){alert="Click claimed ground to select a district area first.";return;}
            UUID target=targetZone>=0&&targetZone<data.districts().size()?data.districts().get(targetZone).id():new UUID(0,0);
            String operation=targetZone>=0?"paint":"create";
            int ty=targetZone>=0&&targetZone<data.districts().size()?data.districts().get(targetZone).type():zoneType;
            String name=zoneName==null?"New District":zoneName.getValue();
            if(name.isBlank()){alert="Enter a district name.";return;}
            DistrictNetwork.CHANNEL.sendToServer(new DistrictNetwork.Action(data.manager(),operation,target,name,ty,
                brushedChunks.stream().mapToLong(Long::longValue).toArray(),data.cx(),data.cz()));
            alert="Submitting district to server...";
        }
        private void openWarMap() {
            Minecraft mc=Minecraft.getInstance();
            if(mc.player==null)return;
            restore();
            // The request uses this physical Manager block, with server-side proximity,
            // ownership, leadership and fog-of-war checks. No forged remote access.
            WarMapNetwork.CHANNEL.sendToServer(new WarMapNetwork.Request(data.manager(),data.manager().getX()>>4,data.manager().getZ()>>4));
            mc.setScreen(null);
        }
        /** Convert the freely moving GUI cursor into a perspective ray in the real world. */
        private BlockPos pickSurface(double mx,double my){
            Minecraft mc=Minecraft.getInstance();
            if(mc.level==null||camera==null||width<1||height<1)return null;
            Vec3 look=camera.getViewVector(1.0f).normalize();
            Vec3 right=new Vec3(-look.z,0,look.x).normalize();
            Vec3 up=right.cross(look).normalize();
            double fov=Math.toRadians(mc.options.fov().get());
            double half=Math.tan(fov*.5);
            double horizontal=(2.0*mx/width-1.0)*half*width/height;
            double vertical=(1.0-2.0*my/height)*half;
            Vec3 aim=look.add(right.scale(horizontal)).add(up.scale(vertical)).normalize();
            Vec3 from=new Vec3(x,y,z),to=from.add(aim.scale(170));
            BlockHitResult hit=mc.level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,camera));
            return hit.getType()==HitResult.Type.BLOCK?hit.getBlockPos():null;
        }
        private int tileIndex(int cx,int cz){
            int mx=cx-data.cx()+DistrictNetwork.SIZE/2,mz=cz-data.cz()+DistrictNetwork.SIZE/2;
            return mx<0||mz<0||mx>=DistrictNetwork.SIZE||mz>=DistrictNetwork.SIZE?-1:mz*DistrictNetwork.SIZE+mx;
        }
        private void selectClaim(double mx,double my){
            BlockPos pos=pickSurface(mx,my);
            if(pos==null){alert="Aim at visible terrain to select a chunk.";return;}
            int cx=pos.getX()>>4,cz=pos.getZ()>>4,idx=tileIndex(cx,cz);
            if(idx<0){alert="Chunk is outside the loaded 3D claim map.";return;}
            if(data.claims()[idx]!=0){alert=data.claims()[idx]==1?"Already owned by your settlement.":"Another faction owns that chunk.";return;}
            selectedClaimChunk=ChunkPos.asLong(cx,cz);
            alert="Selected chunk "+cx+", "+cz+" — review then click CLAIM SELECTED CHUNK.";
        }
        private void claimSelected(){
            if(selectedClaimChunk==null){alert="Click an unclaimed chunk in the 3D world first.";return;}
            int cx=ChunkPos.getX(selectedClaimChunk),cz=ChunkPos.getZ(selectedClaimChunk);
            int index=tileIndex(cx,cz);
            if(index<0||data.claims()[index]!=0){selectedClaimChunk=null;alert="Chunk is no longer unclaimed. Refresh first.";return;}
            DistrictNetwork.CHANNEL.sendToServer(new DistrictNetwork.ClaimChunk(data.manager(),cx,cz,claimTown));
            alert="Claiming "+cx+", "+cz+" — server checking resources and borders...";
        }
        private void paintChunk(double mx,double my){
            BlockPos picked=pickSurface(mx,my);
            if(picked==null){alert="Point at visible land inside your claims.";return;}
            int cx=picked.getX()>>4,cz=picked.getZ()>>4,index=tileIndex(cx,cz);
            if(index<0){alert="That chunk is outside the local zone map.";return;}
            if(data.claims()[index]!=1){alert="Only your faction's claimed chunks can be zoned.";return;}
            if(data.zoning()[index]!=0 && targetZone<0){alert="Already zoned. Choose an existing district to expand or use Districts to edit.";return;}
            if(data.zoning()[index]!=0 && targetZone>=0 && Byte.toUnsignedInt(data.zoning()[index])!=targetZone+1){
                alert="That chunk belongs to a different district.";return;
            }
            long key=ChunkPos.asLong(cx,cz);
            if(brushedChunks.size()<DistrictData.MAX_PAINT || brushedChunks.contains(key)){
                brushedChunks.add(key);alert="Selected "+brushedChunks.size()+" claimed chunk(s).";
            }else alert="Maximum 128 chunks per paint operation.";
        }
        private boolean overHud(double mx,double my){
            if(mx>=7&&mx<=226&&my<54)return true;
            if(mx>=8&&mx<=18+sidebarWidth()&&my>=55&&my<=170)return true;
            if(pane!=Pane.NONE&&mx>=panelX()-4&&my>=54)return true;
            if(mx>=width-205&&my<116)return true;
            return false;
        }
        @Override public boolean mouseClicked(double mx,double my,int button){
            if(pane==Pane.ARCHITECT && (button==0||button==1)){
                if(inFolderList(mx,my)){
                    int i=folderScroll+((int)my-LIBRARY_TOP)/LIBRARY_ROW;
                    if(i>=0&&i<=blueprintFolders.size()){
                        folderFilterIndex=i;blueprintIndex=0;buildScroll=0;
                    }
                    return true;
                }
                if(inDesignFlyout(mx,my)){
                    int row=((int)my-flyoutY()-37)/26;
                    var plans=visibleBlueprints();int i=buildScroll+row;
                    if(my>=flyoutY()+37&&row>=0&&i>=0&&i<plans.size()){
                        blueprintIndex=i;
                        if(button==0)startConstruction();
                        else alert="Selected "+plans.get(i).name()+" for organizing.";
                    }
                    return true;
                }
            }
            if(super.mouseClicked(mx,my,button))return true;
            if(pane==Pane.CLAIMS&&button==0&&!overHud(mx,my)){selectClaim(mx,my);return true;}
            if(zoningMode&&button==0&&!overHud(mx,my)){painting=true;paintChunk(mx,my);return true;}

            return false;
        }
        @Override public boolean mouseReleased(double mx,double my,int button){
            if(button==0&&painting){painting=false;return true;}
            return super.mouseReleased(mx,my,button);
        }
        /** Click-and-drag zoning works like the building brushes in city simulators. */
        @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){
            if(zoningMode&&painting&&button==0&&!overHud(mx,my)){paintChunk(mx,my);return true;}
            if(button==1&&!overHud(mx,my)){
                // Right drag pans the view; middle drag rotates the view.
                Vec3 look=camera==null?new Vec3(0,0,1):camera.getViewVector(1);
                double len=Math.max(.001,Math.hypot(look.x,look.z));
                double fx=look.x/len,fz=look.z/len;
                x+=(fz*dx+fx*dy)*.11;
                z+=(-fx*dx+fz*dy)*.11;
                updateCamera();return true;
            }
            if(button==2&&!overHud(mx,my)){
                yaw=Mth.wrapDegrees(yaw+(float)Math.max(-40,Math.min(40,dx))*.32f);
                pitch=Math.max(35,Math.min(84,pitch+(float)Math.max(-40,Math.min(40,dy))*.32f));
                updateCamera();return true;
            }
            return super.mouseDragged(mx,my,button,dx,dy);
        }
        /** Four quad vertices facing upward, at the top of an actual terrain block. */
        /** Low-complexity terrain edge: tiny, narrow vanilla outline segments.
         * We deliberately use Minecraft's established renderer here instead of
         * hand-building a RenderType.lines vertex format (which crashed on some clients).
         * Narrow boxes eliminate the big ladders/tiles from the old overlay. */
        private void edgeLine(RenderLevelStageEvent event,VertexConsumer lines,Vec3 eye,
                              int cx,int cz,int side,float red,float green,float blue,float alpha){
            Minecraft mc=Minecraft.getInstance();
            if(mc.level==null||!mc.level.hasChunk(cx,cz))return;
            for(int t=0;t<16;t+=8){
                int ax=cx*16,az=cz*16;
                double bx=side==0?ax+.015:side==1?ax+15.985:ax+t;
                double bz=side==2?az+.015:side==3?az+15.985:az+t;
                int sampleX=side==0?ax:side==1?ax+15:ax+t+2;
                int sampleZ=side==2?az:side==3?az+15:az+t+2;
                float ground=mc.level.getHeight(Heightmap.Types.WORLD_SURFACE,sampleX,sampleZ)+.17f;
                double xEnd=(side==0||side==1)?bx+.027:bx+8;
                double zEnd=(side==2||side==3)?bz+.027:bz+8;
                LevelRenderer.renderLineBox(event.getPoseStack(),lines,
                    new AABB(bx-eye.x,ground-eye.y,bz-eye.z,
                             xEnd-eye.x,ground+.022-eye.y,zEnd-eye.z),red,green,blue,alpha);
            }
        }
        private void renderOwnedClaims(RenderLevelStageEvent event){
            if((!showClaims&&pane!=Pane.CLAIMS)||camera==null)return;
            Minecraft mc=Minecraft.getInstance();if(mc.level==null)return;
            Vec3 eye=event.getCamera().getPosition();
            var buffers=mc.renderBuffers().bufferSource();VertexConsumer line=buffers.getBuffer(RenderType.lines());
            int centerX=(int)Math.floor(x/16.0),centerZ=(int)Math.floor(z/16.0);
            for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++){
                int cx=centerX+dx,cz=centerZ+dz,index=tileIndex(cx,cz);
                if(index<0||data.claims()[index]!=1||!mc.level.hasChunk(cx,cz))continue;
                for(int side=0;side<4;side++){
                    int nx=cx+(side==0?-1:side==1?1:0),nz=cz+(side==2?-1:side==3?1:0);
                    int next=tileIndex(nx,nz);if(next>=0&&data.claims()[next]==1)continue;
                    edgeLine(event,line,eye,cx,cz,side,.21f,.91f,.64f,.92f);
                }
            }
            if(pane==Pane.CLAIMS){
                // Never mix a primitive long and a nullable Long in a ternary: Java unboxes
                // the null selectedClaimChunk during claim-panel world rendering.
                Long focus=selectedClaimChunk;
                if(hoverBlock!=null){
                    focus=Long.valueOf(ChunkPos.asLong(hoverBlock.getX()>>4,hoverBlock.getZ()>>4));
                }
                if(focus!=null){
                    int cx=ChunkPos.getX(focus),cz=ChunkPos.getZ(focus),idx=tileIndex(cx,cz);
                    if(idx>=0&&mc.level.hasChunk(cx,cz)){
                        int code=data.claims()[idx];
                        float red=code==0?1f:1f,green=code==0?.80f:.3f,blue=code==0?.23f:.3f;
                        for(int side=0;side<4;side++)edgeLine(event,line,eye,cx,cz,side,red,green,blue,1f);
                    }
                }
            }
            // Fixed-size icon silhouettes on real, already-loaded terrain.
            // Marker packets are bounded to 64 and never trigger chunk loads.
            int drawn=0;
            for(DistrictNetwork.Marker marker:data.markers()){
                if(drawn>=32)break;
                BlockPos pin=new BlockPos(marker.x(),marker.y(),marker.z());
                if(!mc.level.hasChunkAt(pin))continue;
                if(pin.distSqr(BlockPos.containing(x,y,z))>160*160)continue;
                boolean storage=marker.kind().equals("WAREHOUSE");
                double h=storage?pin.getY()+1:mc.level.getHeight(Heightmap.Types.WORLD_SURFACE,pin.getX(),pin.getZ())+1;
                float r=storage?.96f:.98f,g=storage?.74f:.77f,b=storage?.35f:.23f;
                LevelRenderer.renderLineBox(event.getPoseStack(),line,
                    new AABB(pin.getX()-.25-eye.x,h-eye.y,pin.getZ()-.25-eye.z,
                             pin.getX()+1.25-eye.x,h+1.6-eye.y,pin.getZ()+1.25-eye.z),r,g,b,.95f);
                drawn++;
            }
            buffers.endBatch(RenderType.lines());
        }
        private void terrainSquare(VertexConsumer fill,Matrix4f mat,Vec3 view,
                int bx,int by,int bz,int rgb,float opacity) {
            float r=((rgb>>16)&255)/255f,g=((rgb>>8)&255)/255f,b=(rgb&255)/255f;
            float x0=(float)(bx+.003-view.x),x1=(float)(bx+.997-view.x);
            float z0=(float)(bz+.003-view.z),z1=(float)(bz+.997-view.z);
            float h=(float)(by+.08-view.y);
            fill.vertex(mat,x0,h,z0).color(r,g,b,opacity).endVertex();
            fill.vertex(mat,x0,h,z1).color(r,g,b,opacity).endVertex();
            fill.vertex(mat,x1,h,z1).color(r,g,b,opacity).endVertex();
            fill.vertex(mat,x1,h,z0).color(r,g,b,opacity).endVertex();
        }
        /** A 16x16 terrain-conforming tint; no block editing or extra server chunk loads. */
        private void renderChunkBorders(RenderLevelStageEvent event) {
            Minecraft mc=Minecraft.getInstance();if(mc.level==null||camera==null)return;
            Vec3 view=event.getCamera().getPosition();
            var source=mc.renderBuffers().bufferSource();
            VertexConsumer fill=source.getBuffer(RenderType.debugFilledBox());
            Matrix4f mat=event.getPoseStack().last().pose();
            int anchorX=(int)Math.floor(x/16),anchorZ=(int)Math.floor(z/16);
            for(int dz=-4;dz<=4;dz++)for(int dx=-4;dx<=4;dx++) {
                int cx=anchorX+dx,cz=anchorZ+dz,index=tileIndex(cx,cz);
                if(index<0||!mc.level.hasChunk(cx,cz))continue;
                long key=ChunkPos.asLong(cx,cz);
                boolean chosen=brushedChunks.contains(key);
                boolean hovering=hoverBlock!=null&&(hoverBlock.getX()>>4)==cx&&(hoverBlock.getZ()>>4)==cz;
                boolean owned=data.claims()[index]==1;
                int zone=Byte.toUnsignedInt(data.zoning()[index]);
                boolean alreadyZoned=zone>0&&zone<=data.districts().size();
                if(!owned&&!hovering)continue;
                int type=targetZone>=0&&targetZone<data.districts().size()
                    ?data.districts().get(targetZone).type():zoneType;
                int rgb=alreadyZoned?ZONE_COLORS[data.districts().get(zone-1).type()]:ZONE_COLORS[type];
                boolean unavailable=!owned||(alreadyZoned&&!chosen&&(targetZone<0||zone!=targetZone+1));
                if(hovering)rgb=unavailable?0xFFDE6A63:ZONE_COLORS[type];
                if(chosen)rgb=ZONE_COLORS[type];
                if(!hovering&&!chosen&&!alreadyZoned)continue; // Unzoned land stays unobstructed.
                float opacity=hovering?.48f:chosen?.39f:.18f;
                // Render a quad above every topmost terrain block: even hills and rooftops
                // remain comprehensible, unlike a single floating rectangle at chunk center.
                for(int lx=0;lx<16;lx++)for(int lz=0;lz<16;lz++) {
                    int bx=cx*16+lx,bz=cz*16+lz;
                    int by=mc.level.getHeight(Heightmap.Types.WORLD_SURFACE,bx,bz);
                    terrainSquare(fill,mat,view,bx,by,bz,rgb,opacity);
                }
            }
            source.endBatch(RenderType.debugFilledBox());
            var lines=source.getBuffer(RenderType.lines());
            for(int dz=-4;dz<=4;dz++)for(int dx=-4;dx<=4;dx++) {
                int cx=anchorX+dx,cz=anchorZ+dz,index=tileIndex(cx,cz);
                if(index<0||!mc.level.hasChunk(cx,cz))continue;
                boolean owned=data.claims()[index]==1;
                long key=ChunkPos.asLong(cx,cz);
                boolean chosen=brushedChunks.contains(key);
                boolean hovering=hoverBlock!=null&&(hoverBlock.getX()>>4)==cx&&(hoverBlock.getZ()>>4)==cz;
                int zone=Byte.toUnsignedInt(data.zoning()[index]);
                if(!owned&&!hovering)continue;
                int rgb=hovering?(owned?0xFFF6D881:0xFFDE6A63)
                    :chosen?0xFFFFFFFF:zone>0&&zone<=data.districts().size()
                    ?ZONE_COLORS[data.districts().get(zone-1).type()]:0x638779;
                float red=((rgb>>16)&255)/255f,green=((rgb>>8)&255)/255f,blue=(rgb&255)/255f;
                if(hovering||chosen) {
                    // Strong, hill-following frame around the ENTIRE selected 16x16 chunk.
                    for(int i=0;i<16;i++)for(int side=0;side<4;side++) {
                        int bx=(side==0?cx*16:side==1?cx*16+15:cx*16+i);
                        int bz=(side==2?cz*16:side==3?cz*16+15:cz*16+i);
                        int gy=mc.level.getHeight(Heightmap.Types.WORLD_SURFACE,bx,bz);
                        LevelRenderer.renderLineBox(event.getPoseStack(),lines,
                            new AABB(bx+.04-view.x,gy+.09-view.y,bz+.04-view.z,
                                     bx+.96-view.x,gy+.15-view.y,bz+.96-view.z),red,green,blue,1f);
                    }
                } else if(zone>0&&zone<=data.districts().size()) {
                    int ground=mc.level.getHeight(Heightmap.Types.WORLD_SURFACE,cx*16+8,cz*16+8);
                    LevelRenderer.renderLineBox(event.getPoseStack(),lines,
                        new AABB(cx*16+.15-view.x,ground+.25-view.y,cz*16+.15-view.z,
                                 cx*16+15.85-view.x,ground+.32-view.y,cz*16+15.85-view.z),
                        red,green,blue,.48f);
                }
            }
            source.endBatch(RenderType.lines());
        }
        private static boolean isNavigationKey(int key) {
            return switch (key) {
                case GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_D,
                     GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_RIGHT,
                     GLFW.GLFW_KEY_Q, GLFW.GLFW_KEY_E, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT -> true;
                default -> false;
            };
        }
        @Override public boolean keyPressed(int key,int scan,int modifiers) {
            if(key==GLFW.GLFW_KEY_ESCAPE&&pane!=Pane.NONE){setPane(Pane.NONE);return true;}
            if(key==GLFW.GLFW_KEY_ESCAPE)return super.keyPressed(key,scan,modifiers);
            if(key==GLFW.GLFW_KEY_H){showHelp=!showHelp;return true;}
            if(key==GLFW.GLFW_KEY_C){showClaims=!showClaims;refreshWidgets();return true;}
            if(zoneName!=null&&zoneName.isFocused())return super.keyPressed(key,scan,modifiers);
            if(isNavigationKey(key)){keysHeld.add(key);return true;}
            return super.keyPressed(key,scan,modifiers);
        }
        @Override public boolean keyReleased(int key,int scan,int modifiers) {
            if(isNavigationKey(key)){keysHeld.remove(key);return true;}
            return super.keyReleased(key,scan,modifiers);
        }
        private boolean held(int key) {
            Minecraft mc=Minecraft.getInstance();
            return (zoneName==null||!zoneName.isFocused())&&(keysHeld.contains(key)||InputConstants.isKeyDown(mc.getWindow().getWindow(),key));
        }
        private void updateCamera() {
            Minecraft mc=Minecraft.getInstance();
            if(!active||camera==null||mc.level==null)return;
            double deltaX=x-originX,deltaZ=z-originZ,dist=Math.hypot(deltaX,deltaZ);
            if(dist>LIMIT){x=originX+deltaX*LIMIT/dist;z=originZ+deltaZ*LIMIT/dist;}
            double lo=Math.max(mc.level.getMinBuildHeight()+8,originY+12);
            double hi=Math.min(mc.level.getMaxBuildHeight()-5,originY+91);
            y=Math.max(lo,Math.min(hi,y));
            // Render interpolation uses previous coordinates; synchronizing them avoids 20Hz stutter.
            camera.setPos(x,y,z);
            camera.xo=x;camera.yo=y;camera.zo=z;
            camera.setYRot(yaw);camera.yRotO=yaw;
            camera.setXRot(pitch);camera.xRotO=pitch;
        }
        @Override public void tick() {
            Minecraft mc=Minecraft.getInstance();
            if(mc.level==null||mc.player==null||!mc.player.isAlive()||camera==null){onClose();return;}
            // The server-side player stays beside the Manager; the camera has no teleport powers.
            if(mc.player.distanceToSqr(originX,originY+.5,originZ)>64){onClose();}
        }
        /** Drive the RTS camera at render frame-rate instead of 20 ticks/sec. */
        private void advanceCameraForFrame() {
            if(!active||camera==null)return;
            long now=System.nanoTime();
            double dt=lastRenderNanos==0?0:Math.min(.075,(now-lastRenderNanos)/1_000_000_000.0);
            lastRenderNanos=now;
            Minecraft mc=Minecraft.getInstance();
            // Losing window focus must never leave a held movement key stuck.
            if(GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_FOCUSED)==GLFW.GLFW_FALSE){
                keysHeld.clear();return;
            }
            // Rotation must happen BEFORE calculating the movement direction.
            if(held(GLFW.GLFW_KEY_Q))yaw-=95f*(float)dt;
            if(held(GLFW.GLFW_KEY_E))yaw+=95f*(float)dt;
            yaw=Mth.wrapDegrees(yaw);
            updateCamera();
            double forward=(held(GLFW.GLFW_KEY_W)||held(GLFW.GLFW_KEY_UP)?1:0)-(held(GLFW.GLFW_KEY_S)||held(GLFW.GLFW_KEY_DOWN)?1:0);
            double right=(held(GLFW.GLFW_KEY_D)||held(GLFW.GLFW_KEY_RIGHT)?1:0)-(held(GLFW.GLFW_KEY_A)||held(GLFW.GLFW_KEY_LEFT)?1:0);
            double len=Math.hypot(forward,right);
            if(len>0){
                forward/=len;right/=len;
                // Derive WASD movement from the camera entity's real view vector.
                // This keeps controls aligned with what players SEE, even after rotations.
                Vec3 look=camera.getViewVector(1.0f);
                double horizontalLen=Math.hypot(look.x,look.z);
                if(horizontalLen>0.001){
                    double fx=look.x/horizontalLen,fz=look.z/horizontalLen;
                    double speed=(held(GLFW.GLFW_KEY_LEFT_SHIFT)||held(GLFW.GLFW_KEY_RIGHT_SHIFT))?27:14;
                    double oldX=x,oldZ=z;
                    // Correct screen-relative strafe: A moves left, D moves right.
                    x+=(fx*forward-fz*right)*speed*dt;
                    z+=(fz*forward+fx*right)*speed*dt;
                    // Stop at the loaded-terrain boundary, never teleport backwards.
                    if(mc.level==null||!mc.level.hasChunkAt(BlockPos.containing(x,y,z))){
                        x=oldX;z=oldZ;blockedByUnloadedChunk=true;
                    }else blockedByUnloadedChunk=false;
                }
            }
            updateCamera();
        }
        @Override public boolean mouseScrolled(double mx,double my,double amount) {
            if(pane==Pane.ARCHITECT&&inFolderList(mx,my)){
                folderScroll=Math.max(0,Math.min(Math.max(0,blueprintFolders.size()+1-folderRowsVisible()),folderScroll+(amount<0?1:-1)));
                return true;
            }
            if(pane==Pane.ARCHITECT&&inDesignFlyout(mx,my)){
                buildScroll=Math.max(0,Math.min(Math.max(0,visibleBlueprints().size()-planRowsVisible()),buildScroll+(amount<0?1:-1)));
                return true;
            }
            if(pane==Pane.DISTRICTS&&mx>=panelX()&&my>=108){
                districtScroll=Math.max(0,Math.min(Math.max(0,data.districts().size()-districtRowsVisible()),districtScroll+(amount<0?1:-1)));
                refreshWidgets();return true;
            }
            if(overHud(mx,my))return true;
            y-=amount*4.0;updateCamera();return true;
        }
        @Override public void render(GuiGraphics g,int mx,int my,float partialTick) {
            applyDeferredWidgets();
            lastMouseX=mx;lastMouseY=my;
            hoverBlock=(zoningMode||pane==Pane.CLAIMS)&&!overHud(mx,my)?pickSurface(mx,my):null;
            // Identity at top left, a small navigation rail beneath it, and one contextual right dock.
            g.fill(7,7,226,49,HUD_PANEL);
            g.fill(7,7,226,9,HUD_GOLD);
            g.drawString(font,"KINGDOMS & CLAIMS",15,15,HUD_GOLD,false);
            g.drawString(font,font.plainSubstrByWidth(data.faction(),198),15,31,HUD_TEXT,false);
            int rightX=Math.max(232,width-197);
            g.fill(rightX,7,width-7,35,HUD_PANEL);
            g.drawString(font,showHelp?"H  HIDE CONTROLS":"H  CONTROLS",rightX+8,17,HUD_GOLD,false);
            if(showHelp){
                g.fill(rightX,37,width-7,91,HUD_PANEL);
                g.drawString(font,"WASD move  •  Q/E turn",rightX+7,45,HUD_TEXT,false);
                g.drawString(font,"Wheel zoom  •  right drag pan",rightX+7,58,HUD_TEXT,false);
                g.drawString(font,"Middle drag tilt",rightX+7,71,HUD_TEXT,false);
            }
            int sw=sidebarWidth();
            g.fill(7,55,18+sw,200,0xE71B2B31);
            g.fill(7,55,18+sw,57,HUD_GOLD);
            g.drawString(font,"KINGDOM TOOLS",15,59,HUD_GOLD,false);
            if(pane!=Pane.NONE){
                int px=panelX(),pw=panelWidth(),inner=px+12,w=pw-24;
                g.fill(px,55,px+pw,height-11,0xF01A2930);
                g.fill(px,55,px+pw,57,HUD_GOLD);
                String title=switch(pane){
                    case ZONING -> "ZONE LAND";
                    case DISTRICTS -> "DISTRICTS";
                    case CLAIMS -> "CLAIM TERRITORY";
                    case ECONOMY -> "FINANCES";
                    case ARCHITECT -> "BUILDINGS";
                    default -> "";
                };
                g.drawString(font,title,inner,69,HUD_GOLD,false);
                g.drawString(font,"Esc closes this panel",inner,88,0xFFB9C6C2,false);
                if(pane==Pane.CLAIMS){
                    g.drawString(font,"1. Select an unclaimed chunk",inner,151,HUD_TEXT,false);
                    g.drawString(font,"2. Confirm purchase below",inner,171,HUD_TEXT,false);
                    if(selectedClaimChunk!=null){
                        int cx=ChunkPos.getX(selectedClaimChunk),cz=ChunkPos.getZ(selectedClaimChunk);
                        g.drawString(font,"Selected: "+cx+", "+cz,inner,302,HUD_GOLD,false);
                    }
                    g.drawString(font,"16 cobblestone + iron cost",inner,328,HUD_TEXT,false);
                    g.drawString(font,"Server validates borders + permissions",inner,348,HUD_TEXT,false);
                } else if(pane==Pane.ZONING){
                    g.drawString(font,"1. Choose land use",inner,104,HUD_TEXT,false);
                    g.drawString(font,"2. Name district",inner,184,HUD_TEXT,false);
                    if(height>=347)g.drawString(font,"3. Click / drag on owned land",inner,317,HUD_TEXT,false);
                    if(height>=365)g.drawString(font,"Selected: "+brushedChunks.size()+" / "+DistrictData.MAX_PAINT+" chunks",inner,333,0xFFB7E0B9,false);
                    if(height>=384)g.drawString(font,"4. Create or expand above",inner,350,HUD_TEXT,false);
                } else if(pane==Pane.DISTRICTS){
                    g.drawString(font,data.districts().size()+" districts • choose one",inner,101,HUD_TEXT,false);
                    var zone=selectedDistrict();int ay=districtActionsY();
                    if(zone==null)g.drawString(font,"Select a district to edit",inner,ay-16,HUD_TEXT,false);
                    else g.drawString(font,font.plainSubstrByWidth(zone.name()+" • "+zone.chunks()+" chunks • level "+zone.level(),w),inner,ay-16,HUD_TEXT,false);
                } else if(pane==Pane.ECONOMY){
                    g.drawString(font,"GOLD INGOTS  "+data.gold(),inner,121,HUD_GOLD,false);
                    g.drawString(font,"TAX RATE  "+data.tax()+"%",inner,257,HUD_TEXT,false);
                    g.drawString(font,"Residents "+data.population()+" / "+data.capacity(),inner,282,HUD_TEXT,false);
                    g.drawString(font,"Taxes "+data.income()+" nuggets  •  Wages "+data.payroll()+" nuggets",inner,302,HUD_TEXT,false);
                    g.drawString(font,"Happiness "+data.happy()+"%",inner,322,HUD_TEXT,false);
                    g.drawString(font,"Next cycle "+data.seconds()/60+":"+String.format("%02d",data.seconds()%60),inner,344,HUD_TEXT,false);
                    g.drawString(font,"All resources, including gold, are stored at Warehouse Cores",inner,370,HUD_TEXT,false);
                } else if(pane==Pane.ARCHITECT){
                    g.drawString(font,"HOVER FOLDER  →  CHOOSE DESIGN",inner,101,HUD_TEXT,false);
                    var plan=selectedBlueprint();
                    int infoY=height-199;
                    g.drawString(font,plan==null?"No design selected":font.plainSubstrByWidth("Selected: "+plan.name(),w),inner,infoY,HUD_GOLD,false);
                }
            }
            if(zoningMode&&hoverBlock!=null){
                int cx=hoverBlock.getX()>>4,cz=hoverBlock.getZ()>>4,idx=tileIndex(cx,cz);
                boolean own=idx>=0&&data.claims()[idx]==1;
                int infoX=10+sidebarWidth()+15,infoY=54;
                g.fill(infoX,infoY,infoX+165,infoY+39,0xE41A2930);
                g.drawString(font,"CHUNK "+cx+", "+cz,infoX+8,infoY+7,HUD_GOLD,false);
                g.drawString(font,own?"Owned • click to select":"Unclaimed • cannot zone",infoX+8,infoY+22,
                    own?0xFFB9E0AC:0xFFF0AAA1,false);
            }
            if(showClaims&&data.markers()!=null&&!data.markers().isEmpty()){
                int bx=10+sidebarWidth()+15;
                int by=Math.max(104,height-100);
                int shown=0;
                for(DistrictNetwork.Marker m:data.markers()){
                    if(shown>=4)break;
                    if(Math.abs(m.x()-x)>160||Math.abs(m.z()-z)>160)continue;
                    g.fill(bx,by+shown*13,bx+155,by+shown*13+12,0xC81B2B31);
                    g.drawString(font,(m.kind().equals("WAREHOUSE")?"[W] Warehouse":"[+] "+m.kind())+"  "+(m.x()>>4)+","+(m.z()>>4),bx+3,by+2+shown*13,0xFFE0CF9C,false);
                    shown++;
                }
            }
            if(!alert.isBlank()){
                int ay=height-24,base=10+sidebarWidth()+15;
                int textWidth=Math.max(100,width-base-(pane==Pane.NONE?10:panelWidth()+20));
                g.fill(base-4,ay-4,Math.min(width-9,base+textWidth+8),ay+14,0xE51A2930);
                g.drawString(font,font.plainSubstrByWidth(alert,textWidth),base,ay,0xFFFFE6AD,false);
            }
            super.render(g,mx,my,partialTick);
            renderBuildLibrary(g,mx,my);
        }
        private void restore() {
            if(!active)return;
            active=false;
            Minecraft mc=Minecraft.getInstance();
            keysHeld.clear();
            if(mc.getCameraEntity()==camera) {
                if(originalCamera!=null)mc.setCameraEntity(originalCamera);
                else if(mc.player!=null)mc.setCameraEntity(mc.player);
            }
            mc.options.hideGui=originalHideGui;
            if(originalView!=null)mc.options.setCameraType(originalView);
            camera=null;
            lastRenderNanos=0;
        }
        @Override public void onClose(){restore();super.onClose();}
        @Override public void removed(){restore();super.removed();}
        @Override public boolean isPauseScreen(){return false;}
    }
}
