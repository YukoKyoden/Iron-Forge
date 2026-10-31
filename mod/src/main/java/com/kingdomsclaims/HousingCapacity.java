package com.kingdomsclaims;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Convert captured block states into geometric facts without accessing or loading world chunks. */
public final class HousingCapacity {
    private HousingCapacity(){}
    public static HousingMath.Result evaluate(BlueprintData.Plan plan){
        List<HousingMath.Cell> cells=new ArrayList<>(plan.blocks.size());
        for(BlueprintData.Piece piece:plan.blocks){
            BlockState block=NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),piece.state);
            boolean bed=block.getBlock() instanceof BedBlock;
            boolean door=block.getBlock() instanceof DoorBlock;
            boolean collision;
            try {collision=!block.getCollisionShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO).isEmpty();}
            catch(RuntimeException ex){collision=true;}
            // Beds are furniture, never structural walls or roofs.
            boolean structure=collision&&!bed;
            // A door can close a wall but should not count as a walkable floor tile.
            boolean support=collision&&!bed&&!door;
            String part=piece.state.getCompound("Properties").getString("part");
            cells.add(new HousingMath.Cell(piece.x,piece.y,piece.z,false,structure,support,door,
                    bed&&"foot".equals(part),bed&&"head".equals(part),
                    piece.state.getCompound("Properties").getString("facing")));
        }
        return HousingMath.analyze(plan.width,plan.height,plan.depth,cells);
    }
}
