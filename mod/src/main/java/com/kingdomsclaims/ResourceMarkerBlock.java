package com.kingdomsclaims;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;
/** Stateless, inexpensive production block. Surveys and clocks live in SavedData. */
public final class ResourceMarkerBlock extends Block {
    public final String resource;
    public ResourceMarkerBlock(Properties p,String resource){super(p);this.resource=resource;}
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(level instanceof ServerLevel w&&player instanceof ServerPlayer p&&hand==InteractionHand.MAIN_HAND)ResourceMarkers.inspect(w,pos,p,resource);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
        if(state.getBlock()!=next.getBlock()&&level instanceof ServerLevel w)ResourceMarkers.remove(w,pos);
        super.onRemove(state,level,pos,next,moving);
    }
}
