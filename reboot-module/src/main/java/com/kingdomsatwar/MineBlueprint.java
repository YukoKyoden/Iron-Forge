package com.kingdomsatwar;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Deterministic Level 1 tunnel: a safe, small footprint, no world-scale scans. */
public final class MineBlueprint {
    public record Step(BlockPos pos, BlockState state) {}
    private MineBlueprint() {}
    public static List<Step> layout(BlockPos controller, Direction forward) {
        List<Step> steps = new ArrayList<>();
        Direction sideways=forward.getClockWise();
        for(int distance=1;distance<=8;distance++) {
            for(int lateral=-1;lateral<=1;lateral++) {
                BlockPos base=controller.relative(forward,distance).relative(sideways,lateral);
                steps.add(new Step(base.below(), Blocks.COBBLESTONE.defaultBlockState()));
                for(int h=0;h<=2;h++) {
                    boolean framed=(distance==1 || distance==4 || distance==8);
                    boolean beam=framed && (h==2 || lateral!=0);
                    steps.add(new Step(base.above(h),beam
                        ? Blocks.SPRUCE_LOG.defaultBlockState()
                        : Blocks.AIR.defaultBlockState()));
                }
            }
        }
        return steps;
    }
    private static boolean replaceable(BlockState state) {
        return state.isAir() || state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE)
            || state.is(Blocks.TUFF) || state.is(Blocks.ANDESITE)
            || state.is(Blocks.DIORITE) || state.is(Blocks.GRANITE);
    }
    /** Refuses player constructions, ores, chests, water, lava and block entities. */
    public static String check(Level level,List<Step> steps) {
        for(Step step:steps) {
            BlockPos p=step.pos();
            if(!level.hasChunkAt(p)) return "Mine blueprint crosses unloaded terrain at "+p.toShortString()+".";
            if(!level.getWorldBorder().isWithinBounds(p)) return "Blueprint crosses the world border.";
            BlockState existing=level.getBlockState(p);
            if(level.getBlockEntity(p)!=null)
                return "A container or machine is blocking "+p.toShortString()+".";
            if(!replaceable(existing))
                return "Clear the obstruction at "+p.toShortString()+" ("+existing.getBlock().getName().getString()+").";
        }
        return null;
    }
}
