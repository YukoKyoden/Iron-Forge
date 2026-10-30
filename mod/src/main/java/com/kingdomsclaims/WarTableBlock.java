package com.kingdomsclaims;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.BlockHitResult;

/** Tactical headquarters. No chunk loading, teleporting, or background entity simulation. */
public final class WarTableBlock extends Block {
    public WarTableBlock(BlockBehaviour.Properties props){super(props);}
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(!level.isClientSide&&hand==InteractionHand.MAIN_HAND&&player instanceof ServerPlayer sp)WarTableNetwork.open(sp,pos,"");
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
