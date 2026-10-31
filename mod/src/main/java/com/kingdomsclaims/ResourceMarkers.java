package com.kingdomsclaims;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.*;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.*;
/** Place once, survey on placement and every six minutes. Right-click reads the cached survey. */
public final class ResourceMarkers {
    private ResourceMarkers(){}
    public static boolean territory(ServerLevel w,UUID owner,BlockPos p){if(!w.hasChunkAt(p))return false;var f=BarracksService.faction(w,p);return f!=null&&SharedStockpile.owner(w.getServer(),owner).equals(SharedStockpile.owner(w.getServer(),f.id()));}
    public static boolean present(ServerLevel w,RegisteredBuildings.Building b){return b.marker!=null&&territory(w,b.owner,b.marker)&&w.getBlockState(b.marker).is(b.type.equals("mine")?KingdomsClaims.MINE_MARKER.get():KingdomsClaims.LUMBER_MARKER.get());}
    private static RegisteredBuildings.Building at(ServerLevel w,BlockPos p){for(var b:RegisteredBuildings.get(w.getServer()).buildings)if(b.dimension.equals(KingdomData.dim(w))&&p.equals(b.marker))return b;return null;}
    private static RegisteredBuildings.Building register(ServerLevel w,BlockPos p,ServerPlayer player,String type){
        var existing=at(w,p);if(existing!=null)return existing;
        var f=BarracksService.faction(w,p);if(f==null||!KingdomService.authorized(player,w,p,KingdomData.BUILD)){player.sendSystemMessage(Component.literal("Place production markers in your claimed land with BUILD permission."));return null;}
        var registry=RegisteredBuildings.get(w.getServer());if(registry.buildings.size()>=RegisteredBuildings.MAX_BUILDINGS||type.equals("mine")&&registry.buildings.stream().filter(b->b.railMine).count()>=32){player.sendSystemMessage(Component.literal("Registered production limit reached."));return null;}
        var b=RegisteredBuildings.selection(f.id(),KingdomData.dim(w),p,p,type);b.autoProducer=true;b.railMine=type.equals("mine");b.marker=p.immutable();b.next=w.getServer().overworld().getGameTime()+ProductionClock.TICKS;registry.buildings.add(b);
        var result=survey(w,b);b.capacity=result.capacity();b.surveyDetail=result.detail();b.dirty=false;b.nextValidation=b.next;registry.setDirty();return b;
    }
    public static void inspect(ServerLevel w,BlockPos p,ServerPlayer player,String type){
        if(!KingdomService.authorized(player,w,p,KingdomData.BUILD))return;var b=register(w,p,player,type);if(b==null)return;
        long seconds=Math.max(0,(b.next-w.getServer().overworld().getGameTime()+19)/20);
        boolean mine=type.equals("mine");String label=mine?"Mine":"Woodcutter";
        player.sendSystemMessage(Component.literal("["+label+"] "+(b.capacity>0?"+"+ProductionText.quantity(mine?b.capacity:b.capacity*2)+(mine?" iron":" wood")+" every 6 minutes":"PAUSED - "+b.surveyDetail)));
        player.sendSystemMessage(Component.literal(mine?"Connected rails: "+b.connectedRails+" | Productive sections: "+b.capacity:"Found: "+b.detectedLogs+" logs, "+b.detectedLeaves+" leaves | Area: 15x15, 35 up"));
        player.sendSystemMessage(Component.literal("Next cycle in "+ProductionText.time((int)Math.min(Integer.MAX_VALUE,seconds))+". Right-click shows the latest scan."));
    }
    public static void remove(ServerLevel w,BlockPos p){var registry=RegisteredBuildings.get(w.getServer());boolean changed=registry.buildings.removeIf(b->b.autoProducer&&b.dimension.equals(KingdomData.dim(w))&&p.equals(b.marker));if(changed)registry.setDirty();}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void placed(BlockEvent.EntityPlaceEvent e){
        if(e.isCanceled()||!(e.getLevel() instanceof ServerLevel w)||!(e.getEntity() instanceof ServerPlayer p)||!(e.getPlacedBlock().getBlock() instanceof ResourceMarkerBlock marker))return;
        if(BarracksService.faction(w,e.getPos())==null||!KingdomService.authorized(p,w,e.getPos(),KingdomData.BUILD)){e.setCanceled(true);p.sendSystemMessage(Component.literal("Production markers must be inside your claims."));return;}
        inspect(w,e.getPos(),p,marker.resource);
    }
    public static BlockPos findEntrance(ServerLevel w,UUID owner,BlockPos marker){BlockPos best=null;double distance=Double.MAX_VALUE;
        for(int dy=0;dy<=MarkerArea.MINE_DEPTH;dy++)for(int dx=-MarkerArea.RADIUS;dx<=MarkerArea.RADIUS;dx++)for(int dz=-MarkerArea.RADIUS;dz<=MarkerArea.RADIUS;dz++){
            BlockPos p=marker.offset(dx,-dy,dz);if(p.getY()<w.getMinBuildHeight()||!territory(w,owner,p))continue;
            if(w.getBlockState(p).getBlock() instanceof BaseRailBlock){double d=marker.distSqr(p);if(d<distance){best=p;distance=d;}}
        }return best;
    }
    public static RegisteredBuildings.Survey survey(ServerLevel w,RegisteredBuildings.Building b){
        if(!present(w,b))return new RegisteredBuildings.Survey(0,"Paused: marker is missing or its claim changed ownership.");
        if(b.type.equals("mine")){b.entrance=findEntrance(w,b.owner,b.marker);if(b.entrance==null){b.connectedRails=0;return new RegisteredBuildings.Survey(0,"No rail found within 7 blocks horizontally and 35 blocks below. Place beside the mine opening.");}return RailMine.scan(w,b);}
        int logs=0,leaves=0;var registry=RegisteredBuildings.get(w.getServer());
        for(int dx=-MarkerArea.RADIUS;dx<=MarkerArea.RADIUS;dx++)for(int dz=-MarkerArea.RADIUS;dz<=MarkerArea.RADIUS;dz++)for(int dy=1;dy<=MarkerArea.WOOD_HEIGHT;dy++){
            BlockPos p=b.marker.offset(dx,dy,dz);if(p.getY()>=w.getMaxBuildHeight()||!territory(w,b.owner,p))continue;var state=w.getBlockState(p);if(state.is(BlockTags.LEAVES))leaves++;
            if(!state.is(BlockTags.LOGS))continue;boolean reserved=false;
            for(var old:registry.buildings){if(old==b)break;if(!old.type.equals("lumber camp")||!old.dimension.equals(b.dimension))continue;if(old.autoProducer?present(w,old)&&MarkerArea.containsWood(old.marker.getX(),old.marker.getY(),old.marker.getZ(),p.getX(),p.getY(),p.getZ()):old.contains(p)){reserved=true;break;}}
            if(!reserved)logs++;
        }
        b.detectedLogs=logs;b.detectedLeaves=leaves;int amount=MarkerArea.woodOutput(logs,leaves);
        return new RegisteredBuildings.Survey(amount,"15x15 / 35 up: "+logs+" logs, "+leaves+" leaves; +"+amount+" wood / 6 min. Trees stay intact.");
    }
}
