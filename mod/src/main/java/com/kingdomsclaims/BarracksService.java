package com.kingdomsclaims;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import java.util.*;

/** One troop ledger for real barracks and future War Table. Physical deployment spends reserve. */
public final class BarracksService {
    private BarracksService(){}
    public static final int MAX_LOCAL=12, IRON_PER_SOLDIER=3, BREAD_PER_SOLDIER=6;
    public record Faction(UUID id,String type,String name){}
    public static Faction faction(ServerLevel world,BlockPos pos){
        KingdomData d=KingdomData.get(world.getServer());
        var claim=d.claimAt(KingdomData.dim(world),pos.getX()>>4,pos.getZ()>>4);
        if(claim==null)return null;
        var town=d.townFor(claim);
        if(town!=null)return new Faction(town.id,"town",town.name);
        var kingdom=d.ownerAt(KingdomData.dim(world),pos.getX()>>4,pos.getZ()>>4);
        return kingdom==null?null:new Faction(kingdom.id,"kingdom",kingdom.name);
    }
    public static boolean canManage(ServerPlayer player,BlockPos home){
        KingdomData d=KingdomData.get(player.getServer());var claim=d.claimAt(KingdomData.dim(player.serverLevel()),home.getX()>>4,home.getZ()>>4);
        if(claim==null)return false;
        var town=d.townFor(claim);
        if(town!=null){
            if(town.allowed(player.getUUID(),KingdomData.BANNER))return true;
            if(town.parentKingdom!=null){var kingdom=d.kingdoms.get(town.parentKingdom);return kingdom!=null&&kingdom.allowed(player.getUUID(),KingdomData.BANNER);}
            return false;
        }
        var kingdom=d.ownerAt(KingdomData.dim(player.serverLevel()),home.getX()>>4,home.getZ()>>4);
        return kingdom!=null&&kingdom.allowed(player.getUUID(),KingdomData.BANNER);
    }
    public static List<SoldierEntity> localSoldiers(ServerLevel world,BlockPos home){
        return world.getEntitiesOfClass(SoldierEntity.class,new AABB(home).inflate(80),s->s.homeIs(home));
    }
    private static boolean has(ServerPlayer player,Item item,int required){
        if(player.getAbilities().instabuild)return true;
        int count=0;for(var itemStack:player.getInventory().items)if(itemStack.is(item))count+=itemStack.getCount();return count>=required;
    }
    private static void take(ServerPlayer player,Item item,int quantity){
        if(player.getAbilities().instabuild)return;
        for(var stack:player.getInventory().items){if(stack.is(item)){int n=Math.min(quantity,stack.getCount());stack.shrink(n);quantity-=n;if(quantity==0)break;}}
        player.getInventory().setChanged();
    }
    public static String execute(ServerPlayer player,BlockPos home,String action){
        ServerLevel world=player.serverLevel();
        if(!world.getBlockState(home).is(KingdomsClaims.BARRACKS.get())||player.distanceToSqr(home.getX()+.5,home.getY()+.5,home.getZ()+.5)>64)return "Stand within 8 blocks of a placed Barracks Controller.";
        Faction faction=faction(world,home);
        if(faction==null)return "Barracks must stand inside claimed kingdom or town territory.";
        if(!canManage(player,home))return "You need town or kingdom military permission.";
        List<SoldierEntity> nearby=localSoldiers(world,home);
        List<SoldierEntity> ours=new ArrayList<>();for(var soldier:nearby)if(soldier.belongs(faction.id(),faction.type(),home))ours.add(soldier);
        String troopName=faction.type().equals("town")?"Militia":"Soldiers";
        MilitaryData ledger=MilitaryData.get(player.getServer());
        if(action.equals("follow")||action.equals("guard")){
            for(var soldier:ours)soldier.command(player,action.equals("follow"));
            return action.equals("follow")?"Fielded units following on foot. No teleporting.":"Fielded units guarding their barracks.";
        }
        if(action.equals("recall")){
            int returning=0;
            for(var soldier:ours)if(soldier.isRosterBound()&&soldier.getArmyId()==null&&soldier.isAlive())returning++;
            if(returning==0)return "No roster-bound soldiers nearby to return. Earlier v0.3 militia stay legacy guards.";
            // Server-only atomic ledger update; each unit is discarded immediately, avoiding duplicate credit.
            if(!ledger.addReserve(faction.id(),returning))return "Unable to credit returning soldiers.";
            for(var soldier:ours)if(soldier.isRosterBound()&&soldier.getArmyId()==null&&soldier.isAlive())soldier.discard();
            return "Returned "+returning+" surviving "+troopName.toLowerCase(Locale.ROOT)+" to the reserve.";
        }
        int hire=action.equals("hire1")?1:action.equals("hire10")?10:action.equals("hire50")?50:0;
        if(hire>0){
            int capacity=ledger.max(faction.type());
            if((long)ledger.reserve(faction.id())+ledger.assigned(faction.id())+hire>capacity)return "This "+faction.type()+" has reached the "+capacity+"-troop stored capacity.";
            // Three wheat per bread equivalent; local warehouse is the recruitment supply point.
            Map<String,Integer> supplies=Map.of("IRON",IRON_PER_SOLDIER*hire,"WHEAT",BREAD_PER_SOLDIER*hire*3);
            if(!PhysicalWarehouses.canPayLocal(world,faction.id(),home,96,supplies))
                return "Local warehouse needs "+supplies.get("IRON")+" iron and "+supplies.get("WHEAT")+" wheat to recruit "+hire+" troops.";
            if(!ledger.addReserve(faction.id(),hire))return "Could not save recruited troops.";
            if(!PhysicalWarehouses.payLocal(world,faction.id(),home,96,supplies)){
                ledger.removeReserve(faction.id(),hire);return "Warehouse stock changed; recruitment canceled.";
            }
            return "Recruited "+hire+" "+troopName.toLowerCase(Locale.ROOT)+" using local warehouse iron and wheat.";
        }
        int deployed=action.equals("deploy5")?5:action.equals("deploy1")?1:0;
        if(deployed>0){
            if(ours.size()+deployed>MAX_LOCAL)return "Limit: "+MAX_LOCAL+" physical soldiers at this barracks ("+ours.size()+" nearby).";
            if(ledger.reserve(faction.id())<deployed)return "Not enough troops in reserve. Hire some first.";
            List<SoldierEntity> toDeploy=new ArrayList<>();Set<BlockPos> used=new HashSet<>();
            for(int i=0;i<deployed;i++){
                BlockPos spawn=findSpawn(world,home,i,used);
                if(spawn==null)return "Clear safe space around the Barracks Controller before deployment.";
                used.add(spawn);
                SoldierEntity soldier=KingdomsClaims.SOLDIER.get().create(world);
                if(soldier==null)return "Unable to create soldier entity.";
                soldier.moveTo(spawn.getX()+.5,spawn.getY(),spawn.getZ()+.5,world.random.nextFloat()*360f,0);
                soldier.assign(faction.id(),faction.type(),home,true);
                soldier.setCustomName(Component.literal(faction.name+" "+(faction.type().equals("town")?"Militia":"Soldier")));
                soldier.setCustomNameVisible(false);
                toDeploy.add(soldier);
            }
            if(!ledger.removeReserve(faction.id(),deployed))return "Reserve changed before deployment could complete.";
            for(var soldier:toDeploy)if(!world.addFreshEntity(soldier)){
                for(var unit:toDeploy)if(unit.isAlive())unit.discard();
                ledger.addReserve(faction.id(),deployed);return "Spawn failed; troop reserve was restored.";
            }
            return "Deployed "+deployed+" physical "+troopName.toLowerCase(Locale.ROOT)+"; removed them from the numerical reserve.";
        }
        return "Unknown military order.";
    }
    private static BlockPos findSpawn(ServerLevel world,BlockPos center,int offset,Set<BlockPos> used){
        int[][] spots={{2,0},{0,2},{-2,0},{0,-2},{3,2},{-3,2},{3,-2},{-3,-2},{4,0},{0,4},{-4,0},{0,-4}};
        for(int shift=0;shift<spots.length;shift++){
            int[] xy=spots[(offset+shift)%spots.length];BlockPos floor=center.offset(xy[0],0,xy[1]);
            for(int dy=-1;dy<=2;dy++){
                BlockPos pos=floor.offset(0,dy,0);
                if(!used.contains(pos)&&world.getBlockState(pos).isAir()&&world.getBlockState(pos.above()).isAir()
                    &&world.getBlockState(pos.below()).isSolidRender(world,pos.below())
                    &&world.getEntitiesOfClass(SoldierEntity.class,new AABB(pos).inflate(0.3)).isEmpty())return pos;
            }
        }return null;
    }
}
