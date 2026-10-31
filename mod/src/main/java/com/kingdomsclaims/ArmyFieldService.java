package com.kingdomsclaims;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.network.chat.Component;
/** Loaded soldiers and numerical strength are exclusive; never credits missing/unloaded entities. */
public final class ArmyFieldService {
    public static List<SoldierEntity> loaded(ServerLevel w,UUID id){List<SoldierEntity> list=new ArrayList<>();for(var e:w.getAllEntities())if(e instanceof SoldierEntity s&&s.isAlive()&&id.equals(s.getArmyId()))list.add(s);return list;}
    public static boolean pack(MinecraftServer server,MilitaryData.Army a){
        ServerLevel w=RegisteredBuildings.world(server,a.dimension);if(w==null)return a.fielded==0;
        var units=loaded(w,a.id);int n=units.stream().mapToInt(SoldierEntity::representedTroops).sum();
        if(n!=a.fielded||units.stream().anyMatch(s->s.getTarget()!=null))return false;
        for(var s:units)s.discard();a.strength+=n;a.fielded=0;MilitaryData.get(server).setDirty();return true;
    }
    public static void tick(MinecraftServer server){
        var data=MilitaryData.get(server);
        for(var a:data.armies.values()){
            if(a.order.equals("legacy")||OperationsData.get(server).committed(a.id))continue;
            ServerLevel w=RegisteredBuildings.world(server,a.dimension);if(w==null)continue;
            if(a.order.equals("follow")){
                var captain=a.commander==null?null:server.getPlayerList().getPlayer(a.commander);
                if(captain==null||captain.serverLevel()!=w){a.order="hold";a.marching=false;a.commander=null;a.rallyX=(int)Math.round(a.locationX);a.rallyZ=(int)Math.round(a.locationZ);}
                else{a.rallyX=captain.getBlockX();a.rallyZ=captain.getBlockZ();a.marching=Math.hypot(a.locationX-a.rallyX,a.locationZ-a.rallyZ)>4;}
                data.setDirty();
            }
            var units=loaded(w,a.id);
            if(!units.isEmpty()){
                a.locationX=units.stream().mapToDouble(SoldierEntity::getX).average().orElse(a.locationX);a.locationZ=units.stream().mapToDouble(SoldierEntity::getZ).average().orElse(a.locationZ);
                if(!a.order.equals("follow")&&Math.hypot(a.locationX-a.rallyX,a.locationZ-a.rallyZ)<4)a.marching=false;
                boolean observed=units.stream().anyMatch(s->w.players().stream().anyMatch(p->p.distanceToSqr(s)<96*96));
                if(!observed)pack(server,a);data.setDirty();
            }
            boolean near=w.players().stream().anyMatch(p->p.distanceToSqr(a.locationX,p.getY(),a.locationZ)<48*48);
            if(!near||a.strength<=0)continue;
            int local=0;for(var e:w.getAllEntities())if(e instanceof SoldierEntity s&&s.isAlive()&&s.faction()!=null&&SharedStockpile.owner(server,s.faction()).equals(SharedStockpile.owner(server,a.faction))&&Math.hypot(s.getX()-a.locationX,s.getZ()-a.locationZ)<192)local++;
            for(int i=local;i<30&&a.strength>0;i++){
                BlockPos column=new BlockPos((int)Math.floor(a.locationX)+(i%6)*2-5,0,(int)Math.floor(a.locationZ)+(i/6)*2-4);if(!w.hasChunkAt(column))continue;
                BlockPos spot=w.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,column);
                if(!w.getBlockState(spot).isAir()||!w.getBlockState(spot.above()).isAir()||!w.getBlockState(spot.below()).isSolidRender(w,spot.below()))continue;
                var soldier=KingdomsClaims.SOLDIER.get().create(w);if(soldier==null)break;int weight=SquadMath.weight(a.strength,30-i);
                soldier.moveTo(spot.getX()+.5,spot.getY(),spot.getZ()+.5,0,0);soldier.assign(a.faction,a.type,a.origin,true);soldier.assignSquad(a.id,a.uniform,a.formation,i,weight);soldier.setCustomName(Component.literal(a.name+" ["+weight+"]"));
                if(w.addFreshEntity(soldier)){a.strength-=weight;a.fielded+=weight;data.setDirty();}
            }
        }
    }
}
