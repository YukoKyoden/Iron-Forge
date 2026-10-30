package com.kingdomsclaims;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;

/** Keeps marker blocks stationary so persistent banner coordinates never desynchronize. */
public class TerritoryBannerBlock extends Block {
    public TerritoryBannerBlock(BlockBehaviour.Properties properties) { super(properties); }
    @Override public PushReaction getPistonPushReaction(BlockState state) { return PushReaction.BLOCK; }
}
