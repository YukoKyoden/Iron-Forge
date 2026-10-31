package com.kingdomsclaims;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

/** Saved multi-pixel surface map. No world-generation or unloaded chunk access. */
public final class WarMapData extends SavedData {
    public static final int SIZE=64, SUB=4, UNKNOWN=0xFFFF;
    private final Map<String,Map<Long,short[]>> discovered=new HashMap<>();
    private final Map<String,Set<Long>> needsUpgrade=new HashMap<>();
    public static WarMapData get(MinecraftServer server){
        return server.overworld().getDataStorage().computeIfAbsent(WarMapData::load,WarMapData::new,"kingdomsclaims_cartography");
    }
    public short[] tile(String dimension,int cx,int cz){return discovered.getOrDefault(dimension,Map.of()).get(ChunkPos.asLong(cx,cz));}
    private static short rgb444(int rgb,int y,int north){
        // Subtle vanilla-map-style relief shading, without revealing caves or minerals.
        double shade=north>y+2?0.81:north<y-2?1.10:1.0;
        int r=Math.min(15,(int)Math.round(((rgb>>16)&255)*shade/17.0));
        int g=Math.min(15,(int)Math.round(((rgb>>8)&255)*shade/17.0));
        int b=Math.min(15,(int)Math.round((rgb&255)*shade/17.0));
        return (short)((r<<8)|(g<<4)|b);
    }
    public void exploreAround(ServerLevel level,BlockPos playerPos,int radius){
        String dimension=KingdomData.dim(level);
        Map<Long,short[]> known=discovered.computeIfAbsent(dimension,d->new HashMap<>());
        int centerX=playerPos.getX()>>4,centerZ=playerPos.getZ()>>4;
        boolean changed=false;
        if(known.size()>=160000)return;
        for(int dz=-radius;dz<=radius;dz++)for(int dx=-radius;dx<=radius;dx++){
            int cx=centerX+dx,cz=centerZ+dz;long key=ChunkPos.asLong(cx,cz);
            if(known.containsKey(key)&&!needsUpgrade.getOrDefault(dimension,Set.of()).contains(key))continue;
            LevelChunk chunk=level.getChunkSource().getChunkNow(cx,cz);
            if(chunk==null)continue;
            short[] colors=new short[SUB*SUB];
            for(int sz=0;sz<SUB;sz++)for(int sx=0;sx<SUB;sx++){
                int x=(cx<<4)+sx*4+2,z=(cz<<4)+sz*4+2;
                int y=Math.min(level.getMaxBuildHeight()-1,chunk.getHeight(Heightmap.Types.WORLD_SURFACE,x&15,z&15)-1);
                if(y<level.getMinBuildHeight()){colors[sz*SUB+sx]=(short)0x567;continue;}
                BlockPos surface=new BlockPos(x,y,z);BlockState block=chunk.getBlockState(surface);
                int rgb=block.getMapColor(level,surface).col;
                if(rgb==0)rgb=0x597A48;
                int north=Math.min(level.getMaxBuildHeight()-1,chunk.getHeight(Heightmap.Types.WORLD_SURFACE,x&15,Math.max(0,(z-4)&15))-1);
                colors[sz*SUB+sx]=rgb444(rgb,y,north);
            }
            known.put(key,colors);if(needsUpgrade.containsKey(dimension))needsUpgrade.get(dimension).remove(key);changed=true;
        }
        if(changed)setDirty();
    }
    public static WarMapData load(CompoundTag tag){
        WarMapData d=new WarMapData();
        for(Tag entry:tag.getList("Dimensions",Tag.TAG_COMPOUND)){
            CompoundTag dim=(CompoundTag)entry;String name=dim.getString("Name");
            long[] locations=dim.getLongArray("Chunks");byte[] pixels=dim.getByteArray("Pixels");
            int[] legacyColors=dim.getIntArray("Colors");Map<Long,short[]> known=new HashMap<>();
            for(int i=0;i<locations.length;i++){
                short[] tile=new short[SUB*SUB];
                if(pixels.length>=(i+1)*SUB*SUB*2){
                    for(int s=0;s<tile.length;s++){
                        int off=i*SUB*SUB*2+s*2;
                        tile[s]=(short)(((pixels[off]&255)<<8)|(pixels[off+1]&255));
                    }
                }else if(i<legacyColors.length){java.util.Arrays.fill(tile,(short)(legacyColors[i]&0xFFF));d.needsUpgrade.computeIfAbsent(name,k->new HashSet<>()).add(locations[i]);}
                else continue;
                known.put(locations[i],tile);
            }
            d.discovered.put(name,known);
        }
        return d;
    }
    @Override public CompoundTag save(CompoundTag root){
        ListTag list=new ListTag();
        for(var dimension:discovered.entrySet()){
            CompoundTag dim=new CompoundTag();dim.putString("Name",dimension.getKey());
            int size=dimension.getValue().size();long[] chunks=new long[size];byte[] pixels=new byte[size*SUB*SUB*2];int i=0;
            for(var entry:dimension.getValue().entrySet()){
                chunks[i]=entry.getKey();short[] colors=entry.getValue();
                for(int s=0;s<SUB*SUB;s++){
                    int color=colors[s]&0xFFFF,off=i*SUB*SUB*2+s*2;
                    pixels[off]=(byte)(color>>>8);pixels[off+1]=(byte)color;
                }i++;
            }
            dim.putLongArray("Chunks",chunks);dim.putByteArray("Pixels",pixels);list.add(dim);
        }
        root.putInt("Schema",2);root.put("Dimensions",list);return root;
    }
}
