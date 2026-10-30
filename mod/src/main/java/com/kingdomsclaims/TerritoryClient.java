package com.kingdomsclaims;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Territory access opens the same 3D planner used for buildings. No separate 2D claim UI. */
@Mod.EventBusSubscriber(modid=KingdomsClaims.MODID,value=Dist.CLIENT)
public final class TerritoryClient {
    private TerritoryClient(){}
    private static final KeyMapping OPEN=new KeyMapping("key.kingdomsclaims.territory",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_K,"key.categories.kingdomsclaims");
    @Mod.EventBusSubscriber(modid=KingdomsClaims.MODID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class KeyRegistration {
        @SubscribeEvent public static void registerKeys(RegisterKeyMappingsEvent event){event.register(OPEN);}
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        while(OPEN.consumeClick())openKingdomSurvey();
    }
    public static void openTownSurvey(){openKingdomSurvey();}
    public static void openKingdomSurvey(){
        Minecraft mc=Minecraft.getInstance();
        if(mc.player!=null&&mc.screen==null)
            DistrictNetwork.CHANNEL.sendToServer(new DistrictNetwork.OpenHere());
    }
    /** Backwards-compatible old survey reply; no 2D GUI will be opened. */
    public static void accept(TerritoryNetwork.Snapshot ignored){}
}
