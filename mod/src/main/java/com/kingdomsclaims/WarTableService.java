package com.kingdomsclaims;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.Level;

/** Server-owned strategy orders. Faction, coordinates, soldier counts and permissions are validated here. */
public final class WarTableService {
    private WarTableService(){}
    private static final int MAX_DISTANCE=2048, MAX_LOCAL_FIELD=30;
    private static boolean nearby(MilitaryData.Army a,BlockPos table,String dimension){
        return dimension.equals(a.dimension)&&Math.hypot(a.locationX-table.getX(),a.locationZ-table.getZ())<=20;
    }
    private static int loadedArmyUnits(ServerPlayer player,UUID army){
        return player.serverLevel().getEntitiesOfClass(SoldierEntity.class,new AABB(player.blockPosition()).inflate(100),
            e->army.equals(e.getArmyId())&&e.isAlive()).size();
    }
    public static String execute(ServerPlayer player,BlockPos table,String action,String value){
        if(!WarMapNetwork.isAuthorizedControl(player,table))
            return "Stay within 8 blocks of an authorized War Table or Kingdom Planner.";
        if((action.equals("deploy1")||action.equals("deploy5")||action.equals("recall"))
                && !player.serverLevel().getBlockState(table).is(KingdomsClaims.WAR_TABLE.get()))
            return "Physical soldiers must be deployed or recalled from a War Table.";
        BarracksService.Faction faction=BarracksService.faction(player.serverLevel(),table);
        if(faction==null)return "War Table must stand in claimed territory.";
        if(!BarracksService.canManage(player,table))return "Kingdom or town leadership permission required.";
        MilitaryData military=MilitaryData.get(player.getServer());
        if(action.equals("create")){
            String name=value.trim();if(name.length()<3||name.length()>24||!name.matches("[A-Za-z0-9 _-]+"))return "Army names must be 3-24 letters, numbers, spaces, _ or -.";
            for(var a:military.armiesOf(faction.id()))if(a.name.equalsIgnoreCase(name))return "An army with this name already exists.";
            return military.create(faction.id(),faction.type(),name,KingdomData.dim(player.serverLevel()),table)==null?
                "Maximum 15 armies per faction.":"Created "+name+". Assign troops from the faction reserve.";
        }
        String[] parts=value.split("\\|",-1);
        UUID armyId;
        try{armyId=UUID.fromString(parts[0]);}catch(Exception ignored){return "Select a valid army.";}
        MilitaryData.Army army=military.armies.get(armyId);
        if(army==null||!army.faction.equals(faction.id())||!army.type.equals(faction.type()))return "Army not found in this faction.";
        if(!action.equals("status")&&OperationsData.get(player.getServer()).committed(army.id))return "This army is committed to a resource expedition, outpost, or caravan. Finish or abort that operation first.";
        if(action.equals("status"))return army.name+" at "+(int)army.locationX+", "+(int)army.locationZ+(army.marching?" (marching)":" (holding)");
        if(action.equals("march")){
            if(army.strength<=0||army.fielded>0)return "Assign troops and recall deployed units before marching.";
            if(parts.length!=3)return "Enter X and Z rally coordinates.";
            int x,z;try{x=Integer.parseInt(parts[1]);z=Integer.parseInt(parts[2]);}catch(Exception e){return "Rally coordinates must be integers.";}
            if(Math.hypot(x-army.locationX,z-army.locationZ)>MAX_DISTANCE)return "Rally must be within 2048 blocks of your army.";
            army.rallyX=x;army.rallyZ=z;army.marching=Math.hypot(x-army.locationX,z-army.locationZ)>1.5;
            military.setDirty();return army.marching?"March ordered to "+x+", "+z+". Troops travel numerically, no teleporting.":"Army already at rally point.";
        }
        if(action.equals("home")){
            if(army.fielded>0)return "Recall your deployed soldiers first.";
            army.rallyX=army.origin.getX();army.rallyZ=army.origin.getZ();army.marching=Math.hypot(army.rallyX-army.locationX,army.rallyZ-army.locationZ)>1.5;
            military.setDirty();return army.marching?"Ordered march back to original headquarters.":"Army is at headquarters.";
        }
        if(action.equals("hold")){army.marching=false;military.setDirty();return "Army holding position at "+(int)army.locationX+", "+(int)army.locationZ+".";}
        if(action.equals("formation")){
            if(parts.length!=2||!List.of("line","column","wedge").contains(parts[1]))return "Unknown formation.";
            army.formation=parts[1];military.setDirty();
            // Loaded soldiers update immediately. Unloaded soldiers keep their saved previous order until next deployment.
            for(var unit:player.serverLevel().getEntitiesOfClass(SoldierEntity.class,new AABB(table).inflate(96),s->army.id.equals(s.getArmyId())))unit.changeFormation(army.formation);
            return "Formation: "+army.formation+".";
        }
        if(action.equals("uniform")){
            if(parts.length!=2)return "Invalid uniform.";
            int uniform;try{uniform=Integer.parseInt(parts[1]);}catch(Exception e){return "Invalid uniform.";}
            if(uniform<0||uniform>3)return "Choose one of four uniforms.";
            army.uniform=uniform;military.setDirty();
            for(var unit:player.serverLevel().getEntitiesOfClass(SoldierEntity.class,new AABB(table).inflate(96),s->army.id.equals(s.getArmyId())))unit.setUniform(uniform);
            return "Army uniform updated (cosmetic only; armor stats remain unchanged).";
        }
        if(action.equals("deploy1")||action.equals("deploy5")){
            if(army.marching||!nearby(army,table,KingdomData.dim(player.serverLevel())))return "Army must be stationed near this War Table before deployment.";
            int n=action.equals("deploy5")?5:1;
            if(army.strength<n)return "Not enough non-deployed troops in this army.";
            int field=player.serverLevel().getEntitiesOfClass(SoldierEntity.class,new AABB(table).inflate(96),
                e->e.isAlive()&&e.getFactionType().equals(faction.type())).size();
            if(field+n>MAX_LOCAL_FIELD)return "Maximum 30 physical troops per faction in this local field.";
            List<SoldierEntity> spawned=new ArrayList<>();Set<BlockPos> used=new HashSet<>();
            for(int i=0;i<n;i++){
                BlockPos spot=spawnAt(player,table,field+i,used);
                if(spot==null){for(var unit:spawned)unit.discard();return "Clear solid, open space near your War Table.";}
                used.add(spot);
                SoldierEntity unit=KingdomsClaims.SOLDIER.get().create(player.serverLevel());
                if(unit==null){for(var u:spawned)u.discard();return "Couldn't create soldier.";}
                unit.moveTo(spot.getX()+.5,spot.getY(),spot.getZ()+.5,player.getYRot(),0);
                unit.assign(faction.id(),faction.type(),table,true);
                unit.assignArmy(army.id,army.uniform,army.formation,army.fielded+spawned.size(),player);
                unit.setCustomName(Component.literal(army.name+" - "+(faction.type().equals("town")?"Militia":"Soldier")));
                spawned.add(unit);
            }
            // Reserve is not touched: deployed soldiers come specifically from assigned army strength.
            for(var unit:spawned)if(!player.serverLevel().addFreshEntity(unit)){
                for(var added:spawned)added.discard();return "Deployment failed; army strength unchanged.";
            }
            army.strength-=n;army.fielded+=n;military.setDirty();
            return "Deployed "+n+" members of "+army.name+" with custom uniforms. Follow the commander on foot.";
        }
        if(action.equals("recall")){
            List<SoldierEntity> units=player.serverLevel().getEntitiesOfClass(SoldierEntity.class,new AABB(table).inflate(40),
                s->army.id.equals(s.getArmyId())&&s.isAlive());
            if(units.isEmpty())return "No surviving soldiers from this army near this War Table. Bring them back first.";
            int count=units.size();for(var unit:units)unit.discard();
            army.fielded=Math.max(0,army.fielded-count);army.strength+=count;military.setDirty();
            return "Recalled "+count+" surviving soldiers into the assigned army. Casualties remain lost.";
        }
        if(action.equals("split")){
            if(army.marching||army.fielded>0||army.strength<2)return "Stop and recall units; an army needs at least two troops to split.";
            if(military.armyCount(faction.id())>=MilitaryData.MAX_ARMIES)return "15-army faction limit reached.";
            int amount=army.strength/2;String name=army.name+" II";
            for(var existing:military.armiesOf(faction.id()))if(existing.name.equalsIgnoreCase(name))return "Rename the existing '"+name+"' army first.";
            if(name.length()>24)name=army.name.substring(0,Math.min(21,army.name.length()))+" II";
            var divided=military.create(faction.id(),faction.type(),name,army.dimension,army.origin);
            if(divided==null)return "Cannot create another army.";
            divided.locationX=army.locationX;divided.locationZ=army.locationZ;divided.rallyX=army.rallyX;divided.rallyZ=army.rallyZ;
            divided.uniform=army.uniform;divided.formation=army.formation;divided.strength=amount;army.strength-=amount;military.setDirty();
            return "Split "+amount+" troops into "+name+" at this army's current position.";
        }
        boolean atTable=nearby(army,table,KingdomData.dim(player.serverLevel()))&&!army.marching&&army.fielded==0;
        switch(action){
            case "assign10":case "assign50":{
                int amount=action.endsWith("50")?50:10;
                return !atTable?"Army must return to this War Table with no deployed units to receive reinforcements.":
                    military.assign(army,amount)?"Assigned "+amount+" troops to "+army.name+".":"Not enough unassigned reserve.";
            }
            case "return10":case "return50":{
                int amount=action.endsWith("50")?50:10;
                return !atTable?"Army must be stationed near this War Table before returning troops to reserve.":
                    military.withdraw(army,amount)?"Returned "+amount+" troops to reserve.":"Army lacks "+amount+" troops.";
            }
            case "disband":{
                if(army.strength>0||army.fielded>0)return "Return all assigned and physically deployed troops before disbanding.";
                military.armies.remove(army.id);military.setDirty();return "Disbanded empty army "+army.name+".";
            }
            default:return "Unknown War Table action.";
        }
    }
    private static BlockPos spawnAt(ServerPlayer player,BlockPos table,int offset,Set<BlockPos> used){
        int[][] xy={{2,0},{0,2},{-2,0},{0,-2},{3,2},{-3,2},{3,-2},{-3,-2},{4,0},{0,4},{-4,0},{0,-4}};
        for(int k=0;k<xy.length;k++){
            int[] a=xy[(offset+k)%xy.length];BlockPos center=table.offset(a[0],0,a[1]);
            for(int dy=-1;dy<=2;dy++){
                BlockPos pos=center.offset(0,dy,0);
                if(!used.contains(pos)&&player.serverLevel().getBlockState(pos).isAir()&&player.serverLevel().getBlockState(pos.above()).isAir()
                  &&player.serverLevel().getBlockState(pos.below()).isSolidRender(player.serverLevel(),pos.below())
                  &&player.serverLevel().getEntitiesOfClass(SoldierEntity.class,new AABB(pos).inflate(.3)).isEmpty())return pos;
            }
        }return null;
    }
}
