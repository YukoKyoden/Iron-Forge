package com.kingdomsclaims;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

/** Player-shaped biped renderer. Distinct original Minecraft-style town and kingdom skins. */
public final class SoldierRenderer extends MobRenderer<SoldierEntity,PlayerModel<SoldierEntity>> {
    private static final ResourceLocation TOWN=new ResourceLocation(KingdomsClaims.MODID,"textures/entity/town_militia.png");
    private static final ResourceLocation[] UNIFORMS={
        new ResourceLocation(KingdomsClaims.MODID,"textures/entity/uniform_crimson.png"),
        new ResourceLocation(KingdomsClaims.MODID,"textures/entity/uniform_royal.png"),
        new ResourceLocation(KingdomsClaims.MODID,"textures/entity/uniform_forest.png"),
        new ResourceLocation(KingdomsClaims.MODID,"textures/entity/uniform_sand.png")
    };
    private static final ResourceLocation KINGDOM=new ResourceLocation(KingdomsClaims.MODID,"textures/entity/kingdom_soldier.png");
    public SoldierRenderer(EntityRendererProvider.Context context){
        super(context,new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER),false),0.5f);
        addLayer(new ItemInHandLayer<>(this,context.getItemInHandRenderer()));
    }
    @Override public ResourceLocation getTextureLocation(SoldierEntity entity){return entity.getUniform()>=0?UNIFORMS[entity.getUniform()]:"town".equals(entity.getFactionType())?TOWN:KINGDOM;}
}
