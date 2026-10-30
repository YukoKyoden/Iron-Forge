package com.kingdomsclaims;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BucketItem;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.Objects;

/** Claim enforcement and anti-burial checks happen exclusively on the logical server. */
public final class ClaimEvents {
    private ClaimEvents(){}
    private static void deny(ServerPlayer p){p.displayClientMessage(Component.literal("Protected territory or banner plaza."),true);}
    private static boolean protectedFrom(Entity actor,ServerLevel level,BlockPos pos,int permission){
        KingdomData d=KingdomData.get(level.getServer());if(d.claimAt(KingdomData.dim(level),pos.getX()>>4,pos.getZ()>>4)==null)return false;
        return !(actor instanceof ServerPlayer p)||!KingdomService.authorized(p,level,pos,permission);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent e){
        if(!(e.getLevel() instanceof ServerLevel level))return;
        BlockPos pos=e.getPos();Entity actor=e.getEntity();
        if(KingdomService.bannerProtected(level,pos)||protectedFrom(actor,level,pos,KingdomData.BUILD)){
            e.setCanceled(true);if(actor instanceof ServerPlayer p)deny(p);return;
        }
        boolean kingdom=e.getPlacedBlock().is(KingdomsClaims.KINGDOM_BANNER.get());
        boolean town=e.getPlacedBlock().is(KingdomsClaims.TOWN_BANNER.get());
        if((e.getPlacedBlock().is(KingdomsClaims.BARRACKS.get())||e.getPlacedBlock().is(KingdomsClaims.WAR_TABLE.get())||e.getPlacedBlock().is(KingdomsClaims.KINGDOM_MANAGER.get())||e.getPlacedBlock().is(KingdomsClaims.SUPPLY_DEPOT.get())||e.getPlacedBlock().is(KingdomsClaims.STORAGE_EXPANSION.get()))){
            if(!(actor instanceof ServerPlayer p)||BarracksService.faction(level,pos)==null){
                e.setCanceled(true);
                if(actor instanceof ServerPlayer p)p.displayClientMessage(Component.literal("Place military controllers inside claimed kingdom or town land."),false);
            }
            return;
        }
        if(!kingdom&&!town)return;
        if(!(actor instanceof ServerPlayer p)){e.setCanceled(true);return;}
        String kind=kingdom?"KINGDOM":"TOWN";
        String error=KingdomService.validateBanner(p,level,pos,kind);
        if(error!=null){e.setCanceled(true);p.displayClientMessage(Component.literal(error),false);return;}
        KingdomService.registerBanner(p,level,pos,kind);
        p.displayClientMessage(Component.literal(kind+" banner founded at chunk "+(pos.getX()>>4)+", "+(pos.getZ()>>4)+"."),false);
    }
    @SubscribeEvent public static void onMultiPlace(BlockEvent.EntityMultiPlaceEvent e){
        if(!(e.getLevel() instanceof ServerLevel level))return;
        for(var snapshot:e.getReplacedBlockSnapshots())if(KingdomService.bannerProtected(level,snapshot.getPos())||protectedFrom(e.getEntity(),level,snapshot.getPos(),KingdomData.BUILD)){
            e.setCanceled(true);if(e.getEntity() instanceof ServerPlayer p)deny(p);return;
        }
    }
    @SubscribeEvent public static void onBreak(BlockEvent.BreakEvent e){
        if(!(e.getLevel() instanceof ServerLevel level))return;
        BlockPos pos=e.getPos();ServerPlayer p=e.getPlayer() instanceof ServerPlayer sp?sp:null;
        if(KingdomService.bannerProtected(level,pos)||(p!=null&&!KingdomService.authorized(p,level,pos,KingdomData.BREAK))){
            e.setCanceled(true);if(p!=null)deny(p);
        }
    }
    @SubscribeEvent public static void onUse(PlayerInteractEvent.RightClickBlock e){
        if(!(e.getLevel() instanceof ServerLevel level)||!(e.getEntity() instanceof ServerPlayer p))return;
        BlockPos clicked=e.getPos();
        BlockPos pos=BannerStructure.baseOf(level,clicked);
        BlockPos placed=e.getFace()!=null?clicked.relative(e.getFace()):clicked;
        // Banner right-click opens Town Hall without requiring general building permission.
        if(level.getBlockState(pos).is(KingdomsClaims.TOWN_BANNER.get())&&e.getHand()==net.minecraft.world.InteractionHand.MAIN_HAND){
            KingdomData d=KingdomData.get(level.getServer());
            KingdomData.Banner banner=d.bannerAt(KingdomData.dim(level),pos);
            KingdomData.Town town=banner==null||banner.town==null?null:d.towns.get(banner.town);
            TownHallNetwork.openBanner(p,town);
            e.setCanceled(true);e.setCancellationResult(InteractionResult.SUCCESS);return;
        }
        // Kingdom capitals open the same management GUI on its Kingdom tab.
        if(level.getBlockState(pos).is(KingdomsClaims.KINGDOM_BANNER.get())&&e.getHand()==net.minecraft.world.InteractionHand.MAIN_HAND){
            KingdomData d=KingdomData.get(level.getServer());
            KingdomData.Banner banner=d.bannerAt(KingdomData.dim(level),pos);
            KingdomData.Kingdom kingdom=banner==null||banner.kingdom==null?null:d.kingdoms.get(banner.kingdom);
            TownHallNetwork.openKingdomBanner(p,kingdom);
            e.setCanceled(true);e.setCancellationResult(InteractionResult.SUCCESS);return;
        }
        if((level.getBlockState(clicked).is(KingdomsClaims.BARRACKS.get())||level.getBlockState(clicked).is(KingdomsClaims.WAR_TABLE.get())||level.getBlockState(clicked).is(KingdomsClaims.KINGDOM_MANAGER.get())) && e.getHand()==net.minecraft.world.InteractionHand.MAIN_HAND){
            if(!BarracksService.canManage(p,clicked)){
                e.setCanceled(true);e.setCancellationResult(InteractionResult.FAIL);
                p.displayClientMessage(Component.literal("Military headquarters access requires leadership permission."),true);
            }
            return;
        }
        int access=(level.getBlockState(pos).is(KingdomsClaims.SUPPLY_DEPOT.get())||level.getBlockState(pos).is(KingdomsClaims.STORAGE_EXPANSION.get()))
            ||level.getBlockEntity(pos)!=null?KingdomData.STORAGE:KingdomData.BUILD;
        boolean denied=!KingdomService.authorized(p,level,pos,access)
            ||(e.getItemStack().getItem() instanceof BucketItem&&!KingdomService.authorized(p,level,placed,KingdomData.BUILD));
        if(denied){e.setCanceled(true);e.setCancellationResult(InteractionResult.FAIL);deny(p);}
    }
    /** Upgrade old one-block banners lazily. Never force-loads a chunk or replaces player builds. */
    @SubscribeEvent public static void onServerTick(TickEvent.ServerTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;
        var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if(server==null)return;
        if(server.getTickCount()%20==0){MilitaryData.get(server).advance();EconomyService.tick(server);OperationsService.tick(server);BlueprintService.tick(server);}
        if(server.getTickCount()%200==0){
            WarMapData map=WarMapData.get(server);
            for(ServerPlayer p:server.getPlayerList().getPlayers()){map.exploreAround(p.serverLevel(),p.blockPosition(),8);OperationsService.discover(server,KingdomData.dim(p.serverLevel()),p.blockPosition().getX(),p.blockPosition().getZ());}
        }
        if(server.getTickCount()%100!=0)return;
        KingdomData d=KingdomData.get(server);
        for(KingdomData.Banner banner:d.banners.values()){
            ServerLevel level=server.getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,new net.minecraft.resources.ResourceLocation(banner.dimension)));
            if(level!=null&&level.hasChunkAt(banner.pos))BannerStructure.complete(level,banner.pos);
        }
    }
    @SubscribeEvent public static void onTrample(BlockEvent.FarmlandTrampleEvent e){
        if(e.getLevel() instanceof ServerLevel level&&protectedFrom(e.getEntity(),level,e.getPos(),KingdomData.BREAK))e.setCanceled(true);
    }
    @SubscribeEvent public static void onExplosion(ExplosionEvent.Detonate e){
        if(!(e.getLevel() instanceof ServerLevel level))return;
        KingdomData d=KingdomData.get(level.getServer());String dim=KingdomData.dim(level);
        e.getAffectedBlocks().removeIf(pos->d.claimAt(dim,pos.getX()>>4,pos.getZ()>>4)!=null||KingdomService.bannerProtected(level,pos));
    }
    @SubscribeEvent public static void onPiston(PistonEvent.Pre e){
        if(!(e.getLevel() instanceof ServerLevel level))return;
        BlockPos origin=e.getPos();Direction dir=e.getDirection();KingdomData d=KingdomData.get(level.getServer());String dim=KingdomData.dim(level);
        for(int i=0;i<=13;i++){
            BlockPos a=origin.relative(dir,i),b=origin.relative(dir,i+1);
            if(KingdomService.bannerProtected(level,a)||KingdomService.bannerProtected(level,b)
                ||!Objects.equals(d.identity(dim,a.getX()>>4,a.getZ()>>4),d.identity(dim,b.getX()>>4,b.getZ()>>4))) {
                e.setCanceled(true);return;
            }
        }
    }
    @SubscribeEvent public static void onFluidSolidify(BlockEvent.FluidPlaceBlockEvent e){
        if(!(e.getLevel() instanceof ServerLevel level))return;
        KingdomData d=KingdomData.get(level.getServer());String dim=KingdomData.dim(level);BlockPos from=e.getLiquidPos(),to=e.getPos();
        if(!d.identity(dim,to.getX()>>4,to.getZ()>>4).isEmpty()
            &&!Objects.equals(d.identity(dim,from.getX()>>4,from.getZ()>>4),d.identity(dim,to.getX()>>4,to.getZ()>>4)))
            e.setNewState(e.getOriginalState());
    }
}
