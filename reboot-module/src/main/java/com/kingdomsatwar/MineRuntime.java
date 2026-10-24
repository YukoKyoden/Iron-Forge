package com.kingdomsatwar;

import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

/** Physical construction chest, then lightweight production with saved geological reserves. */
public final class MineRuntime {
    private static final MineRuntime INSTANCE=new MineRuntime();
    public static MineRuntime instance(){return INSTANCE;}
    private MinecraftServer server;private MineEconomy mines;private long ticks;
    private MineRuntime(){}
    private Path file(MinecraftServer s){return s.getWorldPath(LevelResource.ROOT).resolve("data/ironforge_mines.properties");}
    private MineEconomy get(MinecraftServer s){
        if(mines==null||server!=s){
            server=s;try{mines=MineEconomy.load(file(s));}
            catch(IOException e){System.err.println("[IronForge] Mine load failed: "+e);mines=new MineEconomy();}
            ticks=0;
        }return mines;
    }
    private static String dim(Level level){return level.dimension().location().toString();}
    private static void tell(ServerPlayer p,String message){p.sendSystemMessage(Component.literal("[Mine] "+message));}
    @SubscribeEvent public void placed(BlockEvent.EntityPlaceEvent event){
        if(!(event.getEntity() instanceof ServerPlayer player))return;
        if(!(event.getLevel() instanceof Level level)||level.isClientSide)return;
        if(!event.getPlacedBlock().is(MineBlocks.CONTROLLER.get()))return;
        BlockPos p=event.getPos();
        WarState game=KingdomsAtWar.instance().get(player.server);
        WarState.Kingdom kingdom=game.kingdom(player.getUUID());
        if(kingdom==null){tell(player,"Create a kingdom at the War Table before placing a Mine Controller.");return;}
        MineEconomy.Result r=get(player.server).register(kingdom.id,dim(level),
            player.serverLevel().getSeed(),p.getX(),p.getY(),p.getZ());
        tell(player,r.message());
        if(r.ok())save();
    }
    @SubscribeEvent public void broken(BlockEvent.BreakEvent event){
        if(!(event.getLevel() instanceof Level level)||level.isClientSide)return;
        if(event.getState().is(MineBlocks.CONTROLLER.get()))controllerRemoved(level,event.getPos());
    }
    /** Also handles explosions/creative replacement; safe if break event fired earlier. */
    public void controllerRemoved(Level level,BlockPos pos){
        if(server==null||level.isClientSide)return;
        if(get(server).removeMine(dim(level),pos.getX(),pos.getY(),pos.getZ()))save();
    }
    public void interact(ServerPlayer player,BlockPos pos){
        MineEconomy model=get(player.server);
        MineEconomy.Mine mine=model.get(MineEconomy.key(dim(player.level()),pos.getX(),pos.getY(),pos.getZ()));
        if(mine==null){tell(player,"Inactive controller. Place it at Y -55 to 55 while belonging to a kingdom.");return;}
        WarState.Kingdom k=KingdomsAtWar.instance().get(player.server).kingdom(player.getUUID());
        if(k==null||!k.id.equals(mine.owner)){tell(player,"You do not own this mine.");return;}
        if(!(player.serverLevel().getBlockEntity(pos) instanceof MineControllerBlockEntity be)){
            tell(player,"Mine construction chest is missing.");return;
        }
        if(player.isShiftKeyDown()){
            if(!mine.built){
                tell(player,be.beginBuild(player.serverLevel()));
            }else{
                MineEconomy.Result r=model.addWorker(mine.id);
                tell(player,r.message());if(r.ok())save();
            }
        }else{
            tell(player,"Deposit 64 Oak Logs, 128 Cobblestone, 16 Iron Ingots. "+be.needs()+
                ". Sneak-right-click to construct when ready.");
            if(mine.built)tell(player,"Deposit "+mine.quality+"/5 | reserve "+mine.reserve+
                " | workers "+mine.workers+"/"+mine.maxWorkers()+
                " | estimated "+Math.min(40,mine.workers*(mine.quality+1))+" iron/min.");
            player.openMenu(be);
        }
    }
    public void constructionComplete(ServerLevel level,BlockPos p){
        MineEconomy model=get(level.getServer());
        MineEconomy.Result r=model.completeBuild(MineEconomy.key(dim(level),p.getX(),p.getY(),p.getZ()));
        save();
        for(ServerPlayer player:level.players())if(player.blockPosition().distSqr(p)<64*64)
            tell(player,r.message());
    }
    public void constructionInterrupted(ServerLevel level,BlockPos p,String why){
        for(ServerPlayer player:level.players())if(player.blockPosition().distSqr(p)<64*64)
            tell(player,"Construction paused: "+why+". Materials already committed; preserve this controller.");
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        MinecraftServer current=ServerLifecycleHooks.getCurrentServer();
        if(current==null)return;
        MineEconomy model=get(current);
        if(++ticks%1200!=0)return;
        WarState game=KingdomsAtWar.instance().get(current);
        boolean produced=false;
        for(MineEconomy.Mine mine:model.all()){
            WarState.Kingdom owner=game.kingdoms.get(mine.owner);
            if(owner==null)continue;
            int amount=model.payout(mine,Math.max(0,MineEconomy.WAREHOUSE_IRON_CAP-owner.iron));
            if(amount>0){owner.iron+=amount;game.markDirty();produced=true;}
        }
        if(produced)KingdomsAtWar.instance().save(current);
        save();
    }
    @SubscribeEvent public void stopping(ServerStoppingEvent event){
        if(server==event.getServer()){save();server=null;mines=null;}
    }
    private void save(){
        if(server==null||mines==null)return;
        try{mines.save(file(server));}
        catch(IOException e){System.err.println("[IronForge] Mine save failed: "+e);}
    }
}
