package com.kingdomsclaims;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.PushReaction;

/** Non-item upper segments for a three-block, server-managed banner. Tier 1 is middle, tier 2 is top. */
public final class BannerExtensionBlock extends Block {
    public static final IntegerProperty TIER=IntegerProperty.create("tier",1,2);
    public BannerExtensionBlock(BlockBehaviour.Properties properties){
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TIER,1));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(TIER);}
    @Override public PushReaction getPistonPushReaction(BlockState state){return PushReaction.BLOCK;}
}
