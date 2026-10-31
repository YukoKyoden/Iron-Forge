package com.kingdomsclaims;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.levelgen.Heightmap;
/** Bounded rail traversal. Never loads chunks or changes tunnel blocks. */
public final class RailMine {
    public static final int MAX_SECTIONS=512,MAX_RADIUS=128,MAX_Y=32;
    public record Result(Set<BlockPos> rails,int sections,String detail){}
    private static Direction[] ends(RailShape shape){return switch(shape){
        case NORTH_SOUTH,ASCENDING_NORTH,ASCENDING_SOUTH->new Direction[]{Direction.NORTH,Direction.SOUTH};
        case EAST_WEST,ASCENDING_EAST,ASCENDING_WEST->new Direction[]{Direction.EAST,Direction.WEST};
        case SOUTH_EAST->new Direction[]{Direction.SOUTH,Direction.EAST};case SOUTH_WEST->new Direction[]{Direction.SOUTH,Direction.WEST};
        case NORTH_EAST->new Direction[]{Direction.NORTH,Direction.EAST};case NORTH_WEST->new Direction[]{Direction.NORTH,Direction.WEST};};}
    private static int rise(RailShape s,Direction d){return s==RailShape.ASCENDING_EAST&&d==Direction.EAST||s==RailShape.ASCENDING_WEST&&d==Direction.WEST||s==RailShape.ASCENDING_NORTH&&d==Direction.NORTH||s==RailShape.ASCENDING_SOUTH&&d==Direction.SOUTH?1:0;}
    private static RailShape shape(ServerLevel w,BlockPos p){BlockState s=w.getBlockState(p);return s.getBlock() instanceof BaseRailBlock r?s.getValue(r.getShapeProperty()):null;}
    private static boolean territory(ServerLevel w,UUID owner,BlockPos p){if(!w.hasChunkAt(p))return false;var f=BarracksService.faction(w,p);return f!=null&&SharedStockpile.owner(w.getServer(),owner).equals(SharedStockpile.owner(w.getServer(),f.id()));}
    private static boolean clear(ServerLevel w,UUID owner,BlockPos p,RailShape shape){
        for(Direction dir:ends(shape))for(int side=-1;side<=1;side++)for(int y=0;y<3;y++){
            BlockPos q=p.relative(dir.getClockWise(),side).above(y);
            if(!territory(w,owner,q))return false;BlockState s=w.getBlockState(q);
            if(!s.getFluidState().isEmpty()||(!(s.getBlock() instanceof BaseRailBlock)&&!s.getCollisionShape(w,q).isEmpty()))return false;
        }return true;
    }
    public static Result survey(ServerLevel w,UUID owner,BlockPos entrance){
        var found=RailNetwork.walk(new RailNetwork.Point(entrance.getX(),entrance.getY(),entrance.getZ()),new RailNetwork.Track(){
            private BlockPos block(RailNetwork.Point p){return new BlockPos(p.x(),p.y(),p.z());}
            public String shape(RailNetwork.Point p){BlockPos pos=block(p);if(!territory(w,owner,pos))return null;RailShape s=RailMine.shape(w,pos);return s==null?null:s.getSerializedName();}
            public boolean clear(RailNetwork.Point p){BlockPos pos=block(p);RailShape s=RailMine.shape(w,pos);return s!=null&&RailMine.clear(w,owner,pos,s);}
        },MAX_SECTIONS,MAX_RADIUS,MAX_Y);
        Set<BlockPos> visited=new LinkedHashSet<>();for(var p:found.rails())visited.add(new BlockPos(p.x(),p.y(),p.z()));int sections=found.sections();
        return new Result(visited,sections,sections+" valid sections at Y <= 32; "+(sections/2.0)+" iron / 6 min (3x3 clear tunnel; max 512 rails)");
    }

    public static BlockPos surface(ServerLevel w,BlockPos entrance){BlockPos top=w.getHeightmapPos(Heightmap.Types.WORLD_SURFACE,entrance);return new BlockPos(entrance.getX(),Math.max(top.getY(),entrance.getY()+3),entrance.getZ());}
    public static boolean markerValid(ServerLevel w,RegisteredBuildings.Building b){return b.marker!=null&&territory(w,b.owner,b.marker)&&w.getBlockState(b.marker).is(KingdomsClaims.MINE_MARKER.get())&&w.canSeeSky(b.marker.above());}
    public static RegisteredBuildings.Survey scan(ServerLevel w,RegisteredBuildings.Building b){
        if(!b.railMine)return new RegisteredBuildings.Survey(0,"Old mine: unregister and select its entrance rail with the wand.");
        if(!markerValid(w,b))return new RegisteredBuildings.Survey(0,"Mine paused: restore the surface Mine Marker and clear sky above it.");
        Result r=survey(w,b.owner,b.min);b.mineRails=r.rails;
        for(var old:RegisteredBuildings.get(w.getServer()).buildings){if(old==b)break;if(old.railMine&&old.dimension.equals(b.dimension)&&Math.abs(old.min.getX()-b.min.getX())<=MAX_RADIUS*2&&Math.abs(old.min.getZ()-b.min.getZ())<=MAX_RADIUS*2){var previous=survey(w,old.owner,old.min);if(r.rails.stream().anyMatch(previous.rails::contains))return new RegisteredBuildings.Survey(0,"Mine paused: connected track overlaps an earlier registered mine.");}}
        return new RegisteredBuildings.Survey(r.sections,r.detail);
    }
}
