package com.kingdomsatwar;

import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only renderer registration; never loaded on a dedicated server. */
@Mod.EventBusSubscriber(modid=KingdomsAtWar.MOD_ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class IronRecruitClient {
    private IronRecruitClient(){}
    @SubscribeEvent public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event){
        event.registerEntityRenderer(IronRecruitRegistry.SOLDIER.get(),Renderer::new);
    }
    private static final class Renderer extends MobRenderer<IronRecruit,VillagerModel<IronRecruit>> {
        private static final ResourceLocation SKIN=new ResourceLocation("minecraft","textures/entity/villager/villager.png");
        Renderer(EntityRendererProvider.Context context){
            super(context,new VillagerModel<>(context.bakeLayer(ModelLayers.VILLAGER)),0.5F);
        }
        @Override public ResourceLocation getTextureLocation(IronRecruit recruit){return SKIN;}
    }
}
