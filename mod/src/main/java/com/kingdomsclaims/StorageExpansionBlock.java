package com.kingdomsclaims;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.material.PushReaction;

/** Chiseled-bookshelf-style, solid single-cube shelf. Seven static fill textures, no render tick. */
public final class StorageExpansionBlock extends HorizontalDirectionalBlock {
    public static final IntegerProperty FILL=IntegerProperty.create("fill",0,6);
    public static final DirectionProperty FACING=HorizontalDirectionalBlock.FACING;
    public StorageExpansionBlock(BlockBehaviour.Properties props){super(props);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(FILL,0));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,FILL);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());
    }
    @Override public BlockState rotate(BlockState state,Rotation rotation){
        return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));
    }
    @Override public BlockState mirror(BlockState state,Mirror mirror){
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
    @Override public PushReaction getPistonPushReaction(BlockState state){return PushReaction.BLOCK;}
    @Override public InteractionResult use(BlockState state,Level world,BlockPos pos,Player p,InteractionHand hand,BlockHitResult hit){
        if(!world.isClientSide&&hand==InteractionHand.MAIN_HAND&&p instanceof ServerPlayer sp){
            BlockPos core=PhysicalWarehouses.findCore(sp.serverLevel(),pos);
            if(core!=null)SupplyDepotNetwork.open(sp,core,"");
            else sp.sendSystemMessage(net.minecraft.network.chat.Component.literal("[Warehouse] Connect this expansion to a Supply Core within your claim."));
        }
        return InteractionResult.sidedSuccess(world.isClientSide);
    }
}
