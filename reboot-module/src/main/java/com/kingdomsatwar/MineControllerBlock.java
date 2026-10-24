package com.kingdomsatwar;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

/** Right-click to open material chest; sneak-right-click to build the selected Level 1 mine. */
public final class MineControllerBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING=HorizontalDirectionalBlock.FACING;
    public MineControllerBlock(){
        super(BlockBehaviour.Properties.copy(Blocks.STONE_BRICKS).strength(3.5f));
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block,BlockState> builder){
        builder.add(FACING);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext ctx){
        return defaultBlockState().setValue(FACING,ctx.getHorizontalDirection());
    }
    @Override public RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){
        return new MineControllerBlockEntity(pos,state);
    }
    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){
        return level.isClientSide ? null : createTickerHelper(type,MineControllerBlockEntity.TYPE.get(),MineControllerBlockEntity::serverTick);
    }
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if(!level.isClientSide && player instanceof ServerPlayer p)
            MineRuntime.instance().interact(p,pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState replacement,boolean moved){
        if(!state.is(replacement.getBlock())){
            BlockEntity entity=level.getBlockEntity(pos);
            if(!level.isClientSide) {
                if(entity instanceof MineControllerBlockEntity be)Containers.dropContents(level,pos,be);
                MineRuntime.instance().controllerRemoved(level,pos);
            }
            level.updateNeighbourForOutputSignal(pos,this);
        }
        super.onRemove(state,level,pos,replacement,moved);
    }
}
