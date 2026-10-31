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

/** Stationary kingdom or town planning desk; no ticking block entity. */
public final class KingdomManagerBlock extends Block {
    public KingdomManagerBlock(BlockBehaviour.Properties properties){super(properties);}
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(!level.isClientSide&&hand==InteractionHand.MAIN_HAND&&player instanceof ServerPlayer sp)ManagerNetwork.open(sp,"");
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
