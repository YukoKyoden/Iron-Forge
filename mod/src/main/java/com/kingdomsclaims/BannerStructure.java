package com.kingdomsclaims;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Visual structure; only base is ownership/controller. Never overwrites another block. */
public final class BannerStructure {
    private BannerStructure(){}
    public static boolean isExtension(BlockState state){
        return state.is(KingdomsClaims.KINGDOM_BANNER_EXTENSION.get()) || state.is(KingdomsClaims.TOWN_BANNER_EXTENSION.get());
    }
    public static boolean isBase(BlockState state){
        return state.is(KingdomsClaims.KINGDOM_BANNER.get()) || state.is(KingdomsClaims.TOWN_BANNER.get());
    }
    public static BlockPos baseOf(ServerLevel level,BlockPos pos){
        BlockState state=level.getBlockState(pos);
        if(isBase(state))return pos;
        if(isExtension(state)){
            int tier=state.getValue(BannerExtensionBlock.TIER);
            BlockPos base=pos.below(tier);
            if(isBase(level.getBlockState(base)))return base;
        }
        return pos;
    }
    private static BlockState upperState(boolean kingdom,int tier){
        return (kingdom?KingdomsClaims.KINGDOM_BANNER_EXTENSION.get():KingdomsClaims.TOWN_BANNER_EXTENSION.get())
            .defaultBlockState().setValue(BannerExtensionBlock.TIER,tier);
    }
    /** The old 1-block banners from existing saves are completed when their airspace is clear. */
    public static void complete(ServerLevel level,BlockPos base){
        BlockState state=level.getBlockState(base);
        if(!isBase(state))return;
        boolean kingdom=state.is(KingdomsClaims.KINGDOM_BANNER.get());
        for(int tier=1;tier<=2;tier++){
            BlockPos at=base.above(tier);
            BlockState expected=upperState(kingdom,tier);
            if(level.getBlockState(at).isAir())level.setBlock(at,expected,3);
        }
    }
    /** Safe removal: only remove the matching extension pieces of this base. */
    public static void remove(ServerLevel level,BlockPos base){
        boolean kingdom=level.getBlockState(base).is(KingdomsClaims.KINGDOM_BANNER.get());
        for(int tier=1;tier<=2;tier++){
            BlockPos pos=base.above(tier);
            BlockState old=level.getBlockState(pos);
            if(old.equals(upperState(kingdom,tier)))level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
        }
        level.setBlock(base,Blocks.AIR.defaultBlockState(),3);
    }
}
