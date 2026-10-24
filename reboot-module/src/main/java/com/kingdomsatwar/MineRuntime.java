package com.kingdomsatwar;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import java.io.IOException;
import java.nio.file.Path;

/** Forge integration: checks blocks only at placement/removal; economy once per minute. */
public final class MineRuntime {
    private static final MineRuntime INSTANCE=new MineRuntime();
    public static MineRuntime instance(){return INSTANCE;}
    private MinecraftServer server; private MineEconomy mines; private long ticks;
    private MineRuntime(){}
    private Path file(MinecraftServer s){return s.getWorldPath(LevelResource.ROOT).resolve("data/ironforge_mines.properties");}
    private MineEconomy get(MinecraftServer s){
        if(mines==null||server!=s){
            server=s;
            try{mines=MineEconomy.load(file(s));}
            catch(IOException e){System.err.println("[IronForge] Mine load failed: "+e);mines=new MineEconomy();}
            ticks=0;
        }
        return mines;
    }
    private static String dim(Level lvl){return lvl.dimension().location().toString();}
    private static void tell(ServerPlayer p,String message){p.sendSystemMessage(Component.literal("[Mine] "+message));}
    @SubscribeEvent public void placed(BlockEvent.EntityPlaceEvent event){
        if(!(event.getEntity() instanceof ServerPlayer player))return;
        if(!(event.getLevel() instanceof Level level)||level.isClientSide)return;
        BlockState state=event.getPlacedBlock();
        boolean controller=state.is(MineBlocks.CONTROLLER.get()),support=state.is(MineBlocks.SUPPORT.get());
        if(!controller&&!support)return;
        BlockPos p=event.getPos();
        WarState game=KingdomsAtWar.instance().get(player.server);
        WarState.Kingdom k=game.kingdom(player.getUUID());
        if(k==null){tell(player,"Create a kingdom before registering mines.");return;}
        String dimension=dim(level);
        MineEconomy model=get(player.server);
        if(controller){
            WarState.Claim claim=game.claimAt(dimension,WarState.chunk(p.getX()),WarState.chunk(p.getZ()));
            if(claim==null||!claim.owner.equals(k.id)){tell(player,"A mine controller must be on your kingdom's claimed land.");return;}
            MineEconomy.Result r=model.register(k.id,dimension,player.serverLevel().getSeed(),p.getX(),p.getY(),p.getZ());
            tell(player,r.message());
            if(r.ok())save();
        }else{
            MineEconomy.Mine m=model.nearest(k.id,dimension,p.getX(),p.getY(),p.getZ());
            if(m==null){tell(player,"No registered mine within 48 blocks.");return;}
            int faces=rockFaces(level,p);
            boolean open=(level.isEmptyBlock(p.north())&&level.isEmptyBlock(p.south()))
                      ||(level.isEmptyBlock(p.east())&&level.isEmptyBlock(p.west()));
            open=open && level.isEmptyBlock(p.above());
            MineEconomy.Result r=model.addSupport(m.id,p.getX(),p.getY(),p.getZ(),faces,open);
            tell(player,r.message());
            if(r.ok())save();
        }
    }
    /** Eight nearby samples on the two rings, only on support placement. */
    private static int rockFaces(Level level,BlockPos p){
        int count=0;
        for(int dist:new int[]{2,3})for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){
            BlockState s=level.getBlockState(p.offset(d[0]*dist,0,d[1]*dist));
            if(s.is(Blocks.STONE)||s.is(Blocks.DEEPSLATE)||s.is(Blocks.TUFF)
                ||s.is(Blocks.ANDESITE)||s.is(Blocks.DIORITE)||s.is(Blocks.GRANITE)
                ||s.is(Blocks.COBBLED_DEEPSLATE))count++;
        }
        return count;
    }
    @SubscribeEvent public void broken(BlockEvent.BreakEvent event){
        if(!(event.getLevel() instanceof Level level)||level.isClientSide)return;
        BlockState state=event.getState();
        boolean controller=state.is(MineBlocks.CONTROLLER.get()),support=state.is(MineBlocks.SUPPORT.get());
        if(!controller&&!support)return;
        if(server==null)return;
        BlockPos p=event.getPos();MineEconomy model=get(server);
        boolean changed=controller?model.removeMine(dim(level),p.getX(),p.getY(),p.getZ()):
           model.removeSupport(dim(level),p.getX(),p.getY(),p.getZ());
        if(changed)save();
    }
    public void interact(ServerPlayer player,BlockPos pos){
        MineEconomy model=get(player.server);
        MineEconomy.Mine mine=model.get(MineEconomy.key(dim(player.level()),pos.getX(),pos.getY(),pos.getZ()));
        if(mine==null){tell(player,"Inactive controller. Place it in claimed underground territory.");return;}
        WarState.Kingdom k=KingdomsAtWar.instance().get(player.server).kingdom(player.getUUID());
        if(k==null||!k.id.equals(mine.owner)){tell(player,"This mine belongs to another kingdom.");return;}
        if(player.isShiftKeyDown()){
            MineEconomy.Result r=model.addWorker(mine.id);tell(player,r.message());if(r.ok())save();
        } else tell(player,"Iron deposit "+mine.quality+"/5 | reserve "+mine.reserve+" | supports "+mine.supports.size()+
             " | workers "+mine.workers+"/"+mine.maxWorkers()+" | +"+
             Math.min(40,Math.min(mine.workers,mine.maxWorkers())*(mine.quality+1))+" iron/min. Sneak-right-click to hire a worker.");
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        MinecraftServer current=ServerLifecycleHooks.getCurrentServer();
        if(current==null)return;MineEconomy model=get(current);
        if(++ticks%1200!=0)return;
        WarState game=KingdomsAtWar.instance().get(current);
        int produced=0;
        for(MineEconomy.Mine m:model.all()){
            WarState.Kingdom owner=game.kingdoms.get(m.owner);
            if(owner==null)continue;
            int amount=model.payout(m,Math.max(0,MineEconomy.WAREHOUSE_IRON_CAP-owner.iron));
            if(amount>0){owner.iron+=amount;game.markDirty();produced+=amount;}
        }
        if(produced>0)KingdomsAtWar.instance().save(current);
        save();
    }
    @SubscribeEvent public void stopping(ServerStoppingEvent event){
        if(server==event.getServer()) {save();server=null;mines=null;}
    }
    private void save(){
        if(server==null||mines==null)return;
        try{mines.save(file(server));}catch(IOException e){System.err.println("[IronForge] Mine save failed: "+e);}
    }
}
