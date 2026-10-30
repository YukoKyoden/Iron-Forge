package com.kingdomsclaims;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import com.mojang.blaze3d.vertex.VertexConsumer;

/** True first-person blueprint tool. Never edits the world client-side. */
@Mod.EventBusSubscriber(modid=KingdomsClaims.MODID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldArchitectClient {
    private WorldArchitectClient(){}
    private static BlueprintNetwork.Preview active;
    private static List<GhostPiece> cached = List.of();
    private static java.util.Set<Long> ownedChunks=java.util.Set.of();
    private static int turns=0,verticalOffset=0;
    private static final double PLACEMENT_REACH=96.0;
    private static final int MAX_GHOST_BLOCKS=1500;
    private static BlockPos anchor;
    private static String notice="";
    private static boolean waiting=false;
    private static boolean repeatPlacement=true;
    private static int cooldownTicks=0;
    private static BlockPos lastPlacedAnchor=null;
    private record GhostPiece(int x,int y,int z,BlockState state){}

    public static void activate(BlueprintNetwork.Preview preview){
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null)return;
        List<GhostPiece> pieces=new ArrayList<>();
        for(var piece:preview.pieces()){
            BlockState state=NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),piece.state);
            if(!state.isAir())pieces.add(new GhostPiece(piece.x,piece.y,piece.z,state));
        }
        java.util.HashSet<Long> allowed=new java.util.HashSet<>();
        for(long key:preview.ownedChunks())allowed.add(key);
        ownedChunks=java.util.Set.copyOf(allowed);
        cached=List.copyOf(pieces);active=preview;turns=0;verticalOffset=0;anchor=null;waiting=false;cooldownTicks=0;lastPlacedAnchor=null;repeatPlacement=true;
        notice="Aim up to 96 blocks away to position the house";
        if(mc.screen!=null)mc.setScreen(null);
        mc.player.displayClientMessage(Component.literal("[Architect] "+preview.name()+" ready. Aim to position • R rotate • Shift + scroll height • Left click build • M toggle repeats • Esc cancel"),false);
    }
    private static void stop(){active=null;cached=List.of();ownedChunks=java.util.Set.of();anchor=null;waiting=false;cooldownTicks=0;lastPlacedAnchor=null;notice="";}
    public static void outcome(BlueprintNetwork.Outcome result){
        Minecraft mc=Minecraft.getInstance();
        waiting=false;notice=result.message();
        if(mc.player!=null)mc.player.displayClientMessage(Component.literal("[Architect] "+result.message()),false);
        if(result.success()){
            if(!repeatPlacement){stop();return;}
            // Keep the selected blueprint ready, but require a new aim point. Prevent
            // accidental duplicate clicks while construction has not yet drawn any blocks.
            lastPlacedAnchor=anchor;
            anchor=null;
            cooldownTicks=10;
            notice="Construction queued! Aim elsewhere and click to place another. Esc to finish.";
        }
    }
    private static BlockPos target(GhostPiece piece){
        int x=piece.x,z=piece.z;
        return switch(turns&3){
            case 1 -> anchor.offset(active.depth()-1-z,piece.y,x);
            case 2 -> anchor.offset(active.width()-1-x,piece.y,active.depth()-1-z);
            case 3 -> anchor.offset(z,piece.y,active.width()-1-x);
            default -> anchor.offset(x,piece.y,z);
        };
    }
    private static int footprintWidth(){return (turns&1)==0?active.width():active.depth();}
    private static int footprintDepth(){return (turns&1)==0?active.depth():active.width();}
    /** A visible, ordinary ground block becomes the north-west reference corner. */
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||active==null)return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null){stop();return;}
        // Minecraft may handle Esc by opening Pause before Forge delivers its key event.
        if(mc.screen!=null){stop();return;}
        if(cooldownTicks>0)cooldownTicks--;
        // Vanilla mc.hitResult only reaches a few blocks. Raycast explicitly for the architect tool.
        HitResult aimed=mc.player.pick(PLACEMENT_REACH,1.0f,false);
        if(aimed instanceof BlockHitResult hit && aimed.getType()==HitResult.Type.BLOCK){
            BlockPos base=hit.getBlockPos();
            anchor=(hit.getDirection()==Direction.UP?base.above():base.relative(hit.getDirection())).offset(0,verticalOffset,0);
        }else anchor=null;
    }
    private static boolean emptyPreviewSpace(){
        Minecraft mc=Minecraft.getInstance();
        if(active==null||anchor==null||mc.level==null)return false;
        if(anchor.getY()<mc.level.getMinBuildHeight()||anchor.getY()+active.height()>mc.level.getMaxBuildHeight())return false;
        // Validate the complete rotated footprint, not only the anchor or visible blocks.
        for(int dx=0;dx<footprintWidth();dx++)for(int dz=0;dz<footprintDepth();dz++){
            BlockPos covered=anchor.offset(dx,0,dz);
            if(!ownedChunks.contains(net.minecraft.world.level.ChunkPos.asLong(covered.getX()>>4,covered.getZ()>>4)))return false;
        }
        for(GhostPiece piece:cached){
            BlockPos pos=target(piece);
            if(!mc.level.hasChunkAt(pos))return false;
            BlockState old=mc.level.getBlockState(pos);
            if(mc.level.getBlockEntity(pos)!=null||!old.getFluidState().isEmpty()||(!old.isAir()&&!old.canBeReplaced()))return false;
        }
        return true;
    }
    @SubscribeEvent public static void key(InputEvent.Key event){
        Minecraft mc=Minecraft.getInstance();
        if(active==null||mc.screen!=null||event.getAction()!=GLFW.GLFW_PRESS)return;
        // Forge keyboard events are not guaranteed cancelable; calling setCanceled on one can crash.
        if(event.getKey()==GLFW.GLFW_KEY_R){turns=(turns+1)&3;notice="Rotated "+turns*90+"°";if(event.isCancelable())event.setCanceled(true);}
        else if(event.getKey()==GLFW.GLFW_KEY_M){repeatPlacement=!repeatPlacement;notice=repeatPlacement?"Repeat placement ON — place as many copies as you want":"Repeat placement OFF — next successful placement exits";if(event.isCancelable())event.setCanceled(true);}
        else if(event.getKey()==GLFW.GLFW_KEY_ESCAPE){stop();if(event.isCancelable())event.setCanceled(true);}
        else if(event.getKey()==GLFW.GLFW_KEY_ENTER){construct();if(event.isCancelable())event.setCanceled(true);}
    }
    @SubscribeEvent public static void mouse(InputEvent.MouseButton.Pre event){
        if(active==null||Minecraft.getInstance().screen!=null)return;
        if(event.getButton()==GLFW.GLFW_MOUSE_BUTTON_LEFT){
            event.setCanceled(true);
            if(event.getAction()==GLFW.GLFW_PRESS)construct();
        }
        // Suppress right-click while placing: avoid accidentally placing an ordinary block.
        if(event.getButton()==GLFW.GLFW_MOUSE_BUTTON_RIGHT)event.setCanceled(true);
    }
    @SubscribeEvent public static void scroll(InputEvent.MouseScrollingEvent event){
        Minecraft mc=Minecraft.getInstance();
        if(active==null||mc.screen!=null||mc.player==null||!mc.player.isShiftKeyDown())return;
        verticalOffset=Math.max(-8,Math.min(16,verticalOffset+(event.getScrollDelta()>0?1:-1)));
        notice="Height offset: "+verticalOffset;
        event.setCanceled(true);
    }
    private static void construct(){
        if(active==null||waiting||cooldownTicks>0)return;
        if(anchor==null){notice="Aim at a block within 96 blocks";return;}
        if(lastPlacedAnchor!=null&&anchor.equals(lastPlacedAnchor)){
            notice="Aim at a NEW location to place another copy";return;
        }
        if(!emptyPreviewSpace()){notice="Red preview: outside your claims or obstructed";return;}
        waiting=true;notice="Checking claims and construction materials...";
        BlueprintNetwork.CHANNEL.sendToServer(new BlueprintNetwork.PlaceWorld(active.id(),anchor,turns));
    }
    /** Real textured block models with partial alpha; no world changes or block entities. */
    @SubscribeEvent public static void worldRender(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||active==null||anchor==null)return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null||mc.screen!=null)return;
        boolean clear=emptyPreviewSpace();
        var pose=event.getPoseStack();
        Vec3 eye=event.getCamera().getPosition();
        var source=mc.renderBuffers().bufferSource();
        var render=mc.getBlockRenderer().getModelRenderer();
        // Block renderers output standard textured model vertices; route them to the translucent layer.
        VertexConsumer consumer=new GhostConsumer(source.getBuffer(RenderType.translucent()),clear?0.57f:0.40f,clear);
        Rotation rotation=new Rotation[]{Rotation.NONE,Rotation.CLOCKWISE_90,Rotation.CLOCKWISE_180,Rotation.COUNTERCLOCKWISE_90}[turns&3];
        int drawn=0;
        for(var part:cached){
            BlockPos pos=target(part);
            if(pos.distSqr(BlockPos.containing(eye))>128*128)continue;
            if(drawn>=MAX_GHOST_BLOCKS)break;
            pose.pushPose();
            try {
                // A few custom block implementations can throw while rotating or selecting a model.
                // Keep the game running and let the server validate placement instead of crashing the client.
                BlockState rotated=part.state.rotate(rotation);
                pose.translate(pos.getX()-eye.x,pos.getY()-eye.y,pos.getZ()-eye.z);
                render.renderModel(pose.last(),consumer,rotated,mc.getBlockRenderer().getBlockModel(rotated),1,1,1,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY);
                drawn++;
            } catch (RuntimeException ignored) {
                // Unsupported ghost block: preview the rest of the structure.
            } finally {
                pose.popPose();
            }
        }
        source.endBatch(RenderType.translucent());
        pose.pushPose();
        var lines=source.getBuffer(RenderType.lines());
        double x=anchor.getX()-eye.x,y=anchor.getY()-eye.y,z=anchor.getZ()-eye.z;
        LevelRenderer.renderLineBox(pose,lines,new AABB(x,y,z,x+footprintWidth(),y+active.height(),z+footprintDepth()),
            clear?0.43f:1f,clear?0.9f:0.3f,clear?0.75f:0.32f,0.75f);
        source.endBatch(RenderType.lines());
        pose.popPose();
    }
    /** Self-contained consumer: real atlas textures, muted alpha, and a subtle cyan cast. */
    private static final class GhostConsumer implements VertexConsumer {
        private final VertexConsumer out;private final float alpha;private final boolean clear;
        GhostConsumer(VertexConsumer out,float alpha,boolean clear){this.out=out;this.alpha=alpha;this.clear=clear;}
        @Override public VertexConsumer vertex(double x,double y,double z){out.vertex(x,y,z);return this;}
        @Override public VertexConsumer color(int r,int g,int b,int a){
            out.color(clear?(int)(r*.80f):(int)(r*.95f),clear?(int)(g*.94f):(int)(g*.40f),clear?b:(int)(b*.35f),Math.max(20,(int)(a*alpha)));return this;
        }
        @Override public VertexConsumer uv(float u,float v){out.uv(u,v);return this;}
        @Override public VertexConsumer overlayCoords(int u,int v){out.overlayCoords(u,v);return this;}
        @Override public VertexConsumer uv2(int u,int v){out.uv2(u,v);return this;}
        @Override public VertexConsumer normal(float x,float y,float z){out.normal(x,y,z);return this;}
        @Override public void endVertex(){out.endVertex();}
        @Override public void defaultColor(int r,int g,int b,int a){out.defaultColor(r,g,b,(int)(a*alpha));}
        @Override public void unsetDefaultColor(){out.unsetDefaultColor();}
    }
    @SubscribeEvent public static void hud(RenderGuiOverlayEvent.Post event){
        Minecraft mc=Minecraft.getInstance();
        if(active==null||mc.screen!=null||mc.player==null||!event.getOverlay().id().getPath().equals("hotbar"))return;
        GuiGraphics g=event.getGuiGraphics();int w=mc.getWindow().getGuiScaledWidth();int h=mc.getWindow().getGuiScaledHeight();
        int cx=w/2,bottom=h-61;
        g.fill(cx-150,bottom-28,cx+150,bottom+30,0xE5192932);
        g.fill(cx-150,bottom-28,cx+150,bottom-26,0xFFE0BC7D);
        g.drawCenteredString(mc.font,"ARCHITECT  /  "+mc.font.plainSubstrByWidth(active.name(),200),cx,bottom-21,0xFFFFE5AA);
        g.drawCenteredString(mc.font,"R rotate  •  Shift+wheel height  •  M multi-place  •  Esc exit",cx,bottom-7,0xFFD9E7DF);
        String status=waiting?"Checking...":anchor==null?"Aim at a solid block":(emptyPreviewSpace()?"PREVIEW CLEAR — server checks claims and materials":"BLOCKED — claim the land or clear existing blocks");
        g.drawCenteredString(mc.font,status,cx,bottom+7,anchor!=null&&emptyPreviewSpace()?0xFF94ECC9:0xFFFFB5A4);
        g.drawCenteredString(mc.font,repeatPlacement?"MULTI-PLACE ON — click repeatedly to build copies":"ONE BUILD — press M for multiple copies",cx,bottom-35,repeatPlacement?0xFF98EACC:0xFFFFCFA1);
        if(!notice.isBlank())g.drawCenteredString(mc.font,mc.font.plainSubstrByWidth(notice,350),cx,bottom+20,0xFFFFDDA3);
    }
}
