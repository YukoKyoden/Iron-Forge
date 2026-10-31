package com.kingdomsclaims;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * EXPERIMENTAL housing lab, v0.3.9. Read-only on-demand survey at player's feet.
 * No NPCs, chunk loading, economic modifications, town changes or persistent housing claims.
 * This is a deliberately bounded prototype, not a final settlement scanner.
 */
public final class HousingSurvey {
    private HousingSurvey() {}
    private static final int MAX_HORIZONTAL = 24;
    private static final int MAX_VERTICAL = 18;
    private static final int MAX_VISITED = 10000;
    private static final int MIN_FLOOR = 9;
    private static final int MIN_WALL_PERCENT = 80;
    private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("housing")
            .then(Commands.literal("scan").executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                Result result = scan(player.serverLevel(), player.blockPosition());
                player.sendSystemMessage(Component.literal("[Housing Lab] " + result.status));
                player.sendSystemMessage(Component.literal("Floor blocks: " + result.floor +
                    " | Floors: " + result.floors + " | Exterior sides: " + result.sides +
                    " | Exterior wall faces: " + result.exteriorFaces));
                if (result.capacity > 0) {
                    player.sendSystemMessage(Component.literal("Estimated civilian capacity: " + result.capacity +
                        " (test estimate, NOT added to your kingdom)"));
                }
                player.sendSystemMessage(Component.literal("Tip: stand INSIDE the building, then /housing scan. " +
                    "Results are experimental; no civilian population or taxes are created."));
                return result.capacity > 0 ? 1 : 0;
            }))
            .then(Commands.literal("help").executes(ctx -> {
                ctx.getSource().sendSuccess(() -> Component.literal(
                    "Housing Lab: /housing scan while inside a custom aboveground house. " +
                    "No bed, door or room-spam bonuses. Scans loaded chunks only; doesn't modify the world."), false);
                return 1;
            }))
            .executes(ctx -> {
                ctx.getSource().sendSuccess(() -> Component.literal("Try /housing scan or /housing help"), false);
                return 1;
            }));
    }

    private static boolean passable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.getBlock() instanceof DoorBlock || state.getBlock() instanceof LadderBlock ||
            state.getBlock() instanceof ScaffoldingBlock || state.getCollisionShape(level, pos).isEmpty();
    }

    /** True if a ceiling / roof block exists reasonably close above this indoor cell. */
    private static boolean covered(ServerLevel level, BlockPos pos) {
        for (int dy = 1; dy <= 18 && pos.getY() + dy < level.getMaxBuildHeight(); dy++) {
            BlockPos up = pos.above(dy);
            if (!level.hasChunkAt(up)) return false;
            BlockState state = level.getBlockState(up);
            if (!state.isAir() && !state.getCollisionShape(level, up).isEmpty()) return true;
        }
        return false;
    }

    private static boolean floorBelow(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        if (!level.hasChunkAt(below)) return false;
        return !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
    }

    /** Inspect a wall in one direction; require actual sky-connected outside AIR beyond 1-3 wall blocks. */
    private static boolean exteriorWall(ServerLevel level, BlockPos interior, Direction side) {
        boolean hasWall = false;
        for (int n = 1; n <= 4; n++) {
            BlockPos pos = interior.relative(side, n);
            if (!level.hasChunkAt(pos)) return false;
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof DoorBlock) {
                // A door by itself isn't sufficient proof of an exterior wall.
                continue;
            }
            if (!state.isAir() && !state.getCollisionShape(level, pos).isEmpty()) {
                hasWall = true;
                continue;
            }
            if (!hasWall) return false;
            // Sky-facing space a short distance beyond an exterior wall.
            // Check nearby horizontal positions to tolerate eaves / overhangs.
            for (int extra = 0; extra <= 2; extra++) {
                BlockPos outside = pos.relative(side, extra);
                if (!level.hasChunkAt(outside)) return false;
                if (passable(level, outside) && level.canSeeSky(outside)) return true;
            }
            return false;
        }
        return false;
    }

    public static Result scan(ServerLevel level, BlockPos origin) {
        if (!level.hasChunkAt(origin)) return new Result("UNAVAILABLE: chunk isn't loaded",0,0,0,0,0);
        if (!passable(level, origin) || !passable(level, origin.above()))
            return new Result("FAILED: stand in an open two-block-tall interior space",0,0,0,0,0);
        if (!covered(level, origin))
            return new Result("FAILED: no enclosed roof above your standing position",0,0,0,0,0);

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        Set<BlockPos> floor = new HashSet<>();
        queue.add(origin);
        boolean hitLimit = false;
        int boundary = 0;
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            if (!visited.add(pos)) continue;
            if (visited.size() > MAX_VISITED) {hitLimit = true; break;}
            if (Math.abs(pos.getX() - origin.getX()) > MAX_HORIZONTAL ||
                Math.abs(pos.getZ() - origin.getZ()) > MAX_HORIZONTAL ||
                Math.abs(pos.getY() - origin.getY()) > MAX_VERTICAL) {boundary++; continue;}
            if (!level.hasChunkAt(pos) || pos.getY() <= level.getMinBuildHeight() ||
                pos.getY() >= level.getMaxBuildHeight()-1) {boundary++; continue;}
            if (!passable(level, pos) || !covered(level, pos)) continue;
            if (floorBelow(level, pos) && passable(level, pos.above())) floor.add(pos);
            for (Direction direction: Direction.values()) {
                BlockPos next=pos.relative(direction);
                if (!visited.contains(next)) queue.add(next);
            }
        }
        if (hitLimit || boundary > 80)
            return new Result("FAILED: space too large or open; scan a smaller, enclosed building",floor.size(),0,0,0,0);
        if (floor.size() < MIN_FLOOR)
            return new Result("FAILED: fewer than 9 usable floor blocks in connected interior",floor.size(),0,0,0,0);

        // Group walkable cells into actual storeys. A table, barrel or other furnishing
        // may create an isolated walkable surface one block above the main floor: that
        // should NOT count as a second storey or earn extra residents.
        Map<Integer, Set<BlockPos>> byLevel = new HashMap<>();
        for (BlockPos p : floor) byLevel.computeIfAbsent(p.getY(), k -> new HashSet<>()).add(p);
        List<Integer> levels = new ArrayList<>();
        byLevel.keySet().stream()
            .filter(y -> byLevel.get(y).size() >= MIN_FLOOR)
            .sorted((a,b) -> Integer.compare(byLevel.get(b).size(), byLevel.get(a).size()))
            .forEach(y -> {
                // Real floors are normally at least 3 blocks apart. This also stops
                // a thick floor or furniture from creating another population level.
                if (levels.stream().allMatch(existing -> Math.abs(existing-y) >= 3)) levels.add(y);
            });
        Set<BlockPos> livingFloor = new HashSet<>();
        for (int y : levels) livingFloor.addAll(byLevel.get(y));
        if (livingFloor.size() < MIN_FLOOR)
            return new Result("FAILED: no full usable floor",livingFloor.size(),0,0,0,0);

        // Measure the perimeter of each storey, not merely whether a handful of
        // outer pillars are sky-exposed. Pavilions have almost entirely OPEN
        // boundaries even though their roof shelters a large floor area.
        int solidEdges = 0, openEdges = 0;
        for (BlockPos p : livingFloor) {
            for (Direction d : SIDES) {
                BlockPos neighbor = p.relative(d);
                if (livingFloor.contains(neighbor)) continue;
                if (!level.hasChunkAt(neighbor)) {
                    openEdges++;
                    continue;
                }
                BlockState wall = level.getBlockState(neighbor);
                if (!passable(level, neighbor) && !wall.getCollisionShape(level,neighbor).isEmpty()) solidEdges++;
                else openEdges++;
            }
        }
        int wallPercent = (int) Math.round(100.0 * solidEdges / Math.max(1, solidEdges + openEdges));
        if (wallPercent < MIN_WALL_PERCENT)
            return new Result("FAILED: too open to count as a house (" + wallPercent +
                "% enclosed; need " + MIN_WALL_PERCENT + "%)", livingFloor.size(),levels.size(),0,0,0);

        int faces = 0;
        Set<Direction> sides = new HashSet<>();
        // Next, verify that the building has genuine sky-exposed exterior walls.
        // A sealed cave can be perfectly enclosed but is not an aboveground home.
        for (BlockPos p : livingFloor) {
            for (Direction d : SIDES) if (exteriorWall(level, p, d)) {faces++; sides.add(d);}
        }
        if (sides.size() < 2 || faces < 4)
            return new Result("FAILED: insufficient sky-exposed outer walls (likely underground)",
                livingFloor.size(),levels.size(),sides.size(),faces,0);

        // Area-only capacity: no rewards for doors, beds or subdividing rooms.
        // Valid second/third floors contribute their actual area (not just 1 floor).
        int residents = Math.max(1, Math.min(500, livingFloor.size()/12));
        return new Result("PASS: enclosed aboveground building (" + wallPercent +
            "% enclosed)",livingFloor.size(),levels.size(),sides.size(),faces,residents);
    }

    public record Result(String status, int floor, int floors, int sides, int exteriorFaces, int capacity) {}
}
