package com.kingdomsclaims;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Lightweight per-dimension record of blocks directly placed by a player after installation.
 * No block-state copies, ticking, structure guesses or extra chunk loading.
 * This is evidence of building, NOT automatic proof that a residence exists.
 */
public final class PlayerBuildData extends SavedData {
    public static final int MAX_BLOCKS = 300_000;
    public static final int MAX_PER_CHUNK = 4096;
    private final LongOpenHashSet placed = new LongOpenHashSet();
    private final Long2IntOpenHashMap chunkCounts = new Long2IntOpenHashMap();
    public static PlayerBuildData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(PlayerBuildData::load, PlayerBuildData::new, "kingdomsclaims_player_built");
    }
    private static PlayerBuildData load(CompoundTag nbt) {
        var data = new PlayerBuildData();
        for (long packed : nbt.getLongArray("Placed")) {
            if (data.placed.size() >= MAX_BLOCKS) break;
            var pos = BlockPos.of(packed);
            long chunk = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
            if (data.chunkCounts.get(chunk) >= MAX_PER_CHUNK) continue;
            if (data.placed.add(packed)) data.chunkCounts.addTo(chunk, 1);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag nbt) {
        nbt.putLongArray("Placed", placed.toLongArray());
        return nbt;
    }
    public boolean add(BlockPos pos) {
        long key = pos.asLong();
        if (placed.contains(key)) return false;
        if (placed.size() >= MAX_BLOCKS) return false;
        long chunk = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunkCounts.get(chunk) >= MAX_PER_CHUNK) return false;
        if (!placed.add(key)) return false;
        chunkCounts.addTo(chunk, 1);
        setDirty();
        return true;
    }
    public boolean remove(BlockPos pos) {
        if (!placed.remove(pos.asLong())) return false;
        long chunk = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        int count = chunkCounts.addTo(chunk, -1);
        if (count <= 1) chunkCounts.remove(chunk);
        setDirty();
        return true;
    }
    public boolean wasPlaced(BlockPos pos) { return placed.contains(pos.asLong()); }
    public int countChunk(int x, int z) { return chunkCounts.get(ChunkPos.asLong(x, z)); }
    public int countAround(int cx, int cz, int radius) {
        int count = 0;
        for (int z = cz-radius; z <= cz+radius; z++)
            for (int x = cx-radius; x <= cx+radius; x++) count += countChunk(x, z);
        return count;
    }
    public int total() { return placed.size(); }
    public boolean full() { return placed.size() >= MAX_BLOCKS; }
}
