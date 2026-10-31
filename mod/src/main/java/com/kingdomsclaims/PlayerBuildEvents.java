package com.kingdomsclaims;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Tracks future player block placements, removes records for player breaks/explosions. */
public final class PlayerBuildEvents {
    private PlayerBuildEvents(){}
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer)) return;
        if (!event.getPlacedBlock().isAir()) PlayerBuildData.get(level).add(event.getPos());
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMultiPlace(BlockEvent.EntityMultiPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer)) return;
        PlayerBuildData data = PlayerBuildData.get(level);
        for (var snapshot : event.getReplacedBlockSnapshots()) data.add(snapshot.getPos());
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getPlayer() instanceof ServerPlayer)
            PlayerBuildData.get(level).remove(event.getPos());
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        PlayerBuildData data = PlayerBuildData.get(level);
        for (var block : event.getAffectedBlocks()) data.remove(block);
    }
    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("builtblocks")
            .executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                PlayerBuildData data = PlayerBuildData.get(player.serverLevel());
                int cx=player.blockPosition().getX()>>4, cz=player.blockPosition().getZ()>>4;
                ctx.getSource().sendSuccess(() -> Component.literal("Tracked player blocks: this chunk "+data.countChunk(cx,cz)
                    +" | nearby 3x3 chunks "+data.countAround(cx,cz,1)+" | dimension total "+data.total()
                    +(data.full()?" (tracking cap reached)":"")+". Counts begin after v0.4.2 installation."),false);
                return 1;
            })
            .then(Commands.literal("look").executes(ctx -> {
                ServerPlayer player=ctx.getSource().getPlayerOrException();
                HitResult hit=player.pick(5,0f,false);
                if (!(hit instanceof BlockHitResult bh)) {
                    ctx.getSource().sendFailure(Component.literal("Look at a block within 5 blocks."));return 0;
                }
                boolean tracked=PlayerBuildData.get(player.serverLevel()).wasPlaced(bh.getBlockPos());
                ctx.getSource().sendSuccess(() -> Component.literal("Block "+bh.getBlockPos().toShortString()+": "
                    +(tracked?"player-placed since v0.4.2":"not recorded as player-placed")+"."),false);
                return 1;
            })));
    }
}
