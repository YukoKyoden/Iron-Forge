package com.kingdomsclaims;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Local bounding-box selection. Never scans chunks or modifies blocks; server authorizes saving. */
@Mod.EventBusSubscriber(modid=KingdomsClaims.MODID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldSelectionClient {
    private WorldSelectionClient() {}
    private static boolean active=false;
    private static boolean registering=false;
    public static void registerBuilding(){activate();registering=true;}
    private static BlockPos first, hover, second;
    private static int topLift=0;
    private static String status="";
    private static boolean waiting=false;
    private static String lastSaveName="";
    private static final int OUTLINE_LIMIT=700;
    private static final double SELECT_REACH=96.0;

    public static void activate(){
        Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;
        registering=false;active=true;first=null;second=null;hover=null;topLift=0;waiting=false;lastSaveName="";
        status="Look at the first corner and left-click.";
        if(mc.screen!=null)mc.setScreen(null);
        mc.player.displayClientMessage(Component.literal("[Architect] Visual selection: click first corner, aim second, click again. Shift+wheel adjusts height. R restarts; Esc cancels."),false);
    }
    public static void stop(){active=false;first=null;second=null;hover=null;waiting=false;topLift=0;status="";}
    public static void outcome(BlueprintNetwork.SelectionOutcome reply){
        Minecraft mc=Minecraft.getInstance();waiting=false;status=reply.message();
        if(mc.player!=null)mc.player.displayClientMessage(Component.literal("[Architect] "+reply.message()),false);
        if(reply.success())stop();
        else {
            status="Save failed: "+reply.message();
            // Keep the selection and the attempted name so the player can retry.
            if(active&&first!=null&&second!=null)mc.setScreen(new SaveScreen());
        }
    }
    private static BlockPos opposite(){
        BlockPos pos=second!=null?second:hover;
        return pos==null?null:pos.offset(0,topLift,0);
    }
    private static boolean withinLimit(){
        BlockPos b=opposite();if(first==null||b==null)return false;
        long w=Math.abs((long)first.getX()-b.getX())+1;
        long h=Math.abs((long)first.getY()-b.getY())+1;
        long d=Math.abs((long)first.getZ()-b.getZ())+1;
        return registering?w<=64&&h<=64&&d<=64&&w*h*d<=32768:w<=BlueprintData.MAX_WIDTH&&h<=BlueprintData.MAX_HEIGHT&&d<=BlueprintData.MAX_DEPTH&&w*h*d<=BlueprintData.MAX_VOLUME;
    }
    private static String metrics(){
        BlockPos b=opposite();if(first==null||b==null)return "Aim at a building block.";
        long w=Math.abs((long)first.getX()-b.getX())+1;
        long h=Math.abs((long)first.getY()-b.getY())+1;
        long d=Math.abs((long)first.getZ()-b.getZ())+1;
        return w+" x "+h+" x "+d+" = "+(w*h*d)+" / "+BlueprintData.MAX_VOLUME+" blocks";
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!active)return;
        Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.level==null){stop();return;}
        // Escape is handled by Minecraft before some Forge key listeners run.
        // Cancel on the next tick whenever a different screen opens (Pause, Inventory, Chat).
        if(mc.screen!=null){if(!(mc.screen instanceof SaveScreen)&&!(mc.screen instanceof BuildingClient.RegisterScreen))stop();return;}
        HitResult aimed=mc.player.pick(SELECT_REACH,1.0f,false);
        if(aimed instanceof BlockHitResult hit && aimed.getType()==HitResult.Type.BLOCK)
            hover=hit.getBlockPos();
        else hover=null;
    }
    @SubscribeEvent public static void click(InputEvent.MouseButton.Pre event){
        if(!active||Minecraft.getInstance().screen!=null)return;
        if(event.getButton()==GLFW.GLFW_MOUSE_BUTTON_RIGHT){event.setCanceled(true);return;}
        if(event.getButton()!=GLFW.GLFW_MOUSE_BUTTON_LEFT)return;
        event.setCanceled(true);
        if(event.getAction()!=GLFW.GLFW_PRESS||waiting)return;
        if(hover==null){status="Aim at an actual building block.";return;}
        if(second!=null){second=null;status="Adjust the second corner, then click again.";return;}
        if(first==null){first=hover;second=null;topLift=0;status="Move to the opposite corner; click to confirm the highlighted selection.";return;}
        if(!withinLimit()){status="Red selection exceeds 20 per side or 4096 total blocks. Move closer or shrink it.";return;}
        second=hover;
        if(registering)Minecraft.getInstance().setScreen(new BuildingClient.RegisterScreen(first,opposite()));else Minecraft.getInstance().setScreen(new SaveScreen());
    }
    @SubscribeEvent public static void key(InputEvent.Key event){
        if(!active||Minecraft.getInstance().screen!=null||event.getAction()!=GLFW.GLFW_PRESS)return;
        if(event.getKey()==GLFW.GLFW_KEY_R){first=null;second=null;topLift=0;status="Selection restarted. Aim at the first corner.";if(event.isCancelable())event.setCanceled(true);}
        else if(event.getKey()==GLFW.GLFW_KEY_ESCAPE){stop();if(event.isCancelable())event.setCanceled(true);}
    }
    @SubscribeEvent public static void scroll(InputEvent.MouseScrollingEvent event){
        Minecraft mc=Minecraft.getInstance();if(!active||mc.screen!=null||first==null||mc.player==null||!mc.player.isShiftKeyDown())return;
        topLift=Math.max(registering?-63:-19,Math.min(registering?63:19,topLift+(event.getScrollDelta()>0?1:-1)));
        status="Adjusting vertical corner: "+topLift;event.setCanceled(true);
    }
    @SubscribeEvent public static void worldRender(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||!active||first==null||opposite()==null)return;
        Minecraft mc=Minecraft.getInstance();if(mc.level==null)return;
        BlockPos other=opposite();boolean valid=withinLimit();
        int minX=Math.min(first.getX(),other.getX()),maxX=Math.max(first.getX(),other.getX());
        int minY=Math.min(first.getY(),other.getY()),maxY=Math.max(first.getY(),other.getY());
        int minZ=Math.min(first.getZ(),other.getZ()),maxZ=Math.max(first.getZ(),other.getZ());
        Vec3 cam=event.getCamera().getPosition();
        var stack=event.getPoseStack();var source=mc.renderBuffers().bufferSource();
        VertexConsumer lines=source.getBuffer(RenderType.lines());
        float red=valid?.25f:1f,green=valid?.98f:.25f,blue=valid?.78f:.27f;
        LevelRenderer.renderLineBox(stack,lines,new AABB(minX-cam.x,minY-cam.y,minZ-cam.z,maxX+1-cam.x,maxY+1-cam.y,maxZ+1-cam.z),red,green,blue,1f);
        // Outline visible non-air blocks. Hard cap avoids expensive 4k+ per-block draws.
        if(valid){
            int drawn=0;
            outer:for(int y=minY;y<=maxY;y++)for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++){
                if(drawn>=OUTLINE_LIMIT)break outer;
                BlockPos pos=new BlockPos(x,y,z);
                if(!mc.level.hasChunkAt(pos)||mc.level.getBlockState(pos).isAir())continue;
                // Interior solids don't need a separate outline.
                if(x>minX&&x<maxX&&z>minZ&&z<maxZ&&y>minY&&y<maxY&&
                   !mc.level.getBlockState(pos.east()).isAir()&&!mc.level.getBlockState(pos.west()).isAir()&&
                   !mc.level.getBlockState(pos.north()).isAir()&&!mc.level.getBlockState(pos.south()).isAir()&&
                   !mc.level.getBlockState(pos.above()).isAir()&&!mc.level.getBlockState(pos.below()).isAir())continue;
                if(pos.distToCenterSqr(cam.x,cam.y,cam.z)>60*60)continue;
                LevelRenderer.renderLineBox(stack,lines,new AABB(x+.025-cam.x,y+.025-cam.y,z+.025-cam.z,x+.975-cam.x,y+.975-cam.y,z+.975-cam.z),red,green,blue,.20f);
                drawn++;
            }
        }
        source.endBatch(RenderType.lines());
    }
    @SubscribeEvent public static void hud(RenderGuiOverlayEvent.Post event){
        if(!active||!event.getOverlay().id().getPath().equals("hotbar"))return;
        Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.screen!=null)return;
        GuiGraphics g=event.getGuiGraphics();int cx=mc.getWindow().getGuiScaledWidth()/2,y=mc.getWindow().getGuiScaledHeight()-94;
        g.fill(cx-175,y-12,cx+175,y+43,0xE71A2930);
        g.fill(cx-175,y-12,cx+175,y-10,0xFFE8C57C);
        g.drawCenteredString(mc.font,(registering?"REGISTER BUILDING  /  ":"BLUEPRINT SELECTION  /  ")+(first==null?"FIRST CORNER":"SECOND CORNER"),cx,y-6,0xFFE8C57C);
        g.drawCenteredString(mc.font,first==null?"Look at first block • Left click to start":metrics(),cx,y+7,first==null||withinLimit()?0xFF98EACC:0xFFFF9B89);
        g.drawCenteredString(mc.font,"Left click confirm  •  Shift+wheel height  •  R restart  •  Esc cancel",cx,y+20,0xFFDFE6E4);
        g.drawCenteredString(mc.font,mc.font.plainSubstrByWidth(status,340),cx,y+32,0xFFFFDDA3);
    }
    private static final class SaveScreen extends Screen {
        private EditBox name;
        SaveScreen(){super(Component.literal("Save House Blueprint"));}
        @Override protected void init(){
            int cx=width/2,cy=height/2;
            name=new EditBox(font,cx-105,cy-19,210,20,Component.literal("House name"));
            name.setMaxLength(28);name.setValue(lastSaveName);name.setHint(Component.literal("Type a unique name"));addRenderableWidget(name);setInitialFocus(name);
            addRenderableWidget(Button.builder(Component.literal("Save blueprint"),b->save()).bounds(cx-105,cy+10,104,21).build());
            addRenderableWidget(Button.builder(Component.literal("Adjust selection"),b->{second=null;Minecraft.getInstance().setScreen(null);}).bounds(cx+3,cy+10,102,21).build());
            addRenderableWidget(Button.builder(Component.literal("Cancel"),b->{stop();Minecraft.getInstance().setScreen(null);}).bounds(cx-52,cy+41,104,19).build());
        }
        private void save(){
            if(waiting||first==null||opposite()==null||!withinLimit())return;
            String n=name.getValue().strip();if(n.length()<2){status="Give the house a name of at least 2 characters.";return;}
            waiting=true;lastSaveName=n;
            BlueprintNetwork.CHANNEL.sendToServer(new BlueprintNetwork.SaveSelected(first,opposite(),n));
            Minecraft.getInstance().setScreen(null);status="Saving blueprint...";
        }
        @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==GLFW.GLFW_KEY_ENTER){save();return true;}return super.keyPressed(key,scan,modifiers);}
        @Override public void onClose(){stop();Minecraft.getInstance().setScreen(null);}
        @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
            renderBackground(g);
            int cx=width/2,cy=height/2;
            g.drawCenteredString(font,"SAVE HOUSE BLUEPRINT",cx,cy-52,0xFFE8C57C);
            g.drawCenteredString(font,metrics(),cx,cy-37,0xFFB5E8C7);
            if(status.startsWith("Save failed"))g.drawCenteredString(font,font.plainSubstrByWidth(status,330),cx,cy+71,0xFFFFA28E);
            super.render(g,mouseX,mouseY,partial);
        }
    }
}
