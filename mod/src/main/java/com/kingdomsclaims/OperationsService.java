package com.kingdomsclaims;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Server-authoritative mission simulation. No chunk loading, free supplies, or duplicate troop ledgers. */
public final class OperationsService {
    private OperationsService(){}
    private static final int RANGE=2048;
    public static int producedPerMinute(String type){return switch(type){
        case "MINE"->4;case "FARM"->8;case "FOREST"->6;case "QUARRY"->6;case "RUINS"->2;default->0;};
    }
    private static final Map<String,Item> GOODS=Map.of("IRON",Items.IRON_INGOT,"COAL",Items.COAL,"LOGS",Items.OAK_LOG,"WHEAT",Items.WHEAT,"STONE",Items.COBBLESTONE,"EMERALDS",Items.EMERALD,"GOLD",Items.GOLD_INGOT,"GOLD_NUGGETS",Items.GOLD_NUGGET);
    public static boolean claimedSite(MinecraftServer server,String dim,int cx,int cz){
        OperationsData ops=OperationsData.get(server);
        for(var s:ops.sites.values())if(s.dim.equals(dim)&&s.x>>4==cx&&s.z>>4==cz&&!s.stage.equals("EXHAUSTED")&&!s.stage.equals("IDLE"))return true;
        return false;
    }
    private static long scramble(long x){x^=x>>>33;x*=0xff51afd7ed558ccdL;x^=x>>>33;x*=0xc4ceb9fe1a85ec53L;return x^(x>>>33);}
    /** Find candidates ONLY near settlements, outside every protected claim. Never generate distant chunks. */
    public static void discover(MinecraftServer server,String dim,int x,int z){
        OperationsData ops=OperationsData.get(server);if(ops.sites.size()>=OperationsData.SITE_CAP)return;
        KingdomData land=KingdomData.get(server);WarMapData map=WarMapData.get(server);
        int cx=x>>4,cz=z>>4;
        for(int dz=-8;dz<=8;dz++)for(int dx=-8;dx<=8;dx++){
            int sx=cx+dx,sz=cz+dz;if(Math.floorMod(sx,4)!=1||Math.floorMod(sz,4)!=1)continue;
            if(map.tile(dim,sx,sz)==null||land.claimAt(dim,sx,sz)!=null)continue;
            long hash=scramble((((long)sx)<<32)^(sz&0xffffffffL)^dim.hashCode());if(Math.floorMod(hash,3)!=0)continue;
            boolean close=false,tooClose=false;
            for(int r=-10;r<=10;r++)for(int t=-10;t<=10;t++){
                if(land.claimAt(dim,sx+r,sz+t)!=null){int range=Math.max(Math.abs(r),Math.abs(t));if(range<2)tooClose=true;else if(range<=10)close=true;}
            }
            if(!close||tooClose||ops.nearest(dim,(sx<<4)+8,(sz<<4)+8,56)!=null)continue;
            OperationsData.Site s=new OperationsData.Site();s.dim=dim;s.x=(sx<<4)+8;s.z=(sz<<4)+8;
            s.type=OperationsData.TYPES[(int)Math.floorMod(hash>>>7,OperationsData.TYPES.length)];
            s.total=64+(int)Math.floorMod(hash>>>13,129);if("RUINS".equals(s.type))s.total=Math.max(8,s.total/8);
            ops.sites.put(s.id,s);ops.setDirty();
        }
    }
    private static void order(MilitaryData.Army a,int x,int z){a.rallyX=x;a.rallyZ=z;a.marching=Math.hypot(a.locationX-x,a.locationZ-z)>1.5;}
    private static boolean at(MilitaryData.Army a,int x,int z){return Math.hypot(a.locationX-x,a.locationZ-z)<2.5&&!a.marching;}
    private static void release(MilitaryData.Army a){if(a!=null){order(a,a.origin.getX(),a.origin.getZ());}}
    /** Operations are initiated only while at an authorized War Table and within map view. */
    public static String execute(ServerPlayer p,BlockPos table,String action,String value){
        if(!WarMapNetwork.isAuthorizedControl(p,table))return "Stand near an authorized War Table or Kingdom Planner.";
        BarracksService.Faction faction=BarracksService.faction(p.serverLevel(),table);
        if(faction==null||!BarracksService.canManage(p,table))return "Military leadership and claimed headquarters required.";
        OperationsData d=OperationsData.get(p.getServer());MilitaryData mil=MilitaryData.get(p.getServer());String dim=KingdomData.dim(p.serverLevel());
        String[] args=value.split("\\|",-1);
        if(action.equals("withdraw")){
            if(args.length!=2||!GOODS.containsKey(args[0]))return "Choose an available resource.";
            int amount;try{amount=Integer.parseInt(args[1]);}catch(Exception e){return "Invalid amount.";}
            if(amount<1||amount>64)return "Invalid withdrawal amount.";
            int taken=PhysicalWarehouses.takeToPlayer(p,faction.id(),table,args[0],amount);
            return taken>0?"Withdrew "+taken+" "+args[0]+" from War Table warehouse.":"Local warehouse empty or inventory full.";
        }
        if(action.equals("depositmax")){
            if(args.length!=2||!GOODS.containsKey(args[0]))return "Choose goods to deposit.";
            int amount;try{amount=Integer.parseInt(args[1]);}catch(Exception e){return "Invalid deposit limit.";}
            int moved=PhysicalWarehouses.storeFromPlayer(p,faction.id(),table,args[0],amount);
            return moved>0?"Stored "+moved+" "+args[0]+" at the War Table's local warehouse.":"No nearby warehouse space or carried stock.";
        }
        if(action.equals("deposit")){
            if(args.length!=2||!GOODS.containsKey(args[0]))return "Choose goods to deposit.";
            int amount;try{amount=Integer.parseInt(args[1]);}catch(Exception e){return "Invalid deposit amount.";}
            if(EconomyService.carried(p,args[0])<amount)return "Not enough carried resources.";
            int moved=PhysicalWarehouses.storeFromPlayer(p,faction.id(),table,args[0],amount);
            return moved>0?"Stored "+moved+" "+args[0]+" at the local warehouse.":"No warehouse within 96 blocks or storage is full.";
        }
        if(!EconomyService.funded(p.getServer(),faction.id())&&(action.equals("harvest")||action.equals("outpost")||action.equals("scout")))
            return "Upkeep unpaid: "+EconomyService.upkeepStatus(p.getServer(),faction.id())+". Automatically resumes when funded.";
        if(args.length<2)return "Select an army and operation target.";
        UUID armyId,targetId;try{armyId=UUID.fromString(args[0]);targetId=UUID.fromString(args[1]);}catch(Exception e){return "Select valid army and site.";}
        MilitaryData.Army army=mil.armies.get(armyId);
        if(army==null||!army.faction.equals(faction.id())||!army.dimension.equals(dim))return "Army does not belong to your faction.";
        OperationsData.Site s=d.sites.get(targetId);OperationsData.Caravan caravan=d.caravans.get(targetId);
        if(action.equals("raidcaravan")){
            if(caravan==null||!caravan.dim.equals(dim)||caravan.faction.equals(faction.id()))return "No enemy caravan selected.";
            if(!ready(army,d,5)||caravan.attacker!=null||Math.hypot(caravan.locationX-army.locationX,caravan.locationZ-army.locationZ)>RANGE)return "Army unavailable, caravan already contested, or too far.";
            caravan.attacker=army.id;caravan.attackerFaction=faction.id();order(army,(int)caravan.locationX,(int)caravan.locationZ);mil.setDirty();d.setDirty();return "Intercept order issued. Your troops are committed until the raid resolves.";
        }
        if(s==null||!s.dim.equals(dim))return "Operation site is not available in this dimension.";
        KingdomData land=KingdomData.get(p.getServer());
        if(land.claimAt(dim,s.x>>4,s.z>>4)!=null)return "Operations cannot be run inside claimed kingdom or town territory.";
        if(Math.hypot(army.locationX-s.x,army.locationZ-s.z)>RANGE)return "Site is farther than 2048 blocks from that army.";
        if(action.equals("scout")){
            if(s.scouted.contains(faction.id()))return "Already scouted: exact yield and duration are visible.";
            if(!ready(army,d,5))return "Choose a stationary, uncommitted army with at least five troops.";
            if(!s.stage.equals("IDLE")&&!faction.id().equals(s.owner))return "Enemy occupies the site; contest it instead.";
            if(!s.stage.equals("IDLE"))return "Site is currently occupied.";
            s.army=army.id;s.owner=faction.id();s.stage="SCOUT";s.workingSeconds=0;order(army,s.x,s.z);mil.setDirty();d.setDirty();return "Scouts dispatched. Exact yield and extraction time reveal on arrival; army returns to HQ.";
        }
        if(action.equals("harvest")){
            if(!ready(army,d,10))return "Need at least 10 free, stationary troops.";
            if(!s.stage.equals("IDLE")&&!s.stage.equals("ABANDONED"))return "Site occupied. Attack first or choose another site.";
            if(s.progress>=100||s.total<=0)return "Deposit already exhausted.";
            s.army=army.id;s.owner=faction.id();s.stage="OPERATE";order(army,s.x,s.z);mil.setDirty();d.setDirty();return "Expedition started. Troops committed until goods return home. Stockpile is stealable.";
        }
        if(action.equals("outpost")){
            if(!ready(army,d,10))return "Need at least 10 uncommitted troops for an outpost.";
            if(!s.stage.equals("IDLE")&&!s.stage.equals("ABANDONED"))return "Site is occupied. Only wilderness sites can become outposts.";
            if(d.outposts(faction.id())>=8)return "Limit: eight outposts per faction.";
            for(var post:d.sites.values())if("OUTPOST".equals(post.stage)&&post.dim.equals(dim)&&Math.hypot(post.x-s.x,post.z-s.z)<64)
                return "Another outpost controls the nearby wilderness; contest it before establishing yours.";
            s.army=army.id;s.owner=faction.id();s.stage="BUILD_OUTPOST";s.workingSeconds=0;order(army,s.x,s.z);mil.setDirty();d.setDirty();return "Building expedition dispatched. Army stays as outpost garrison once built.";
        }
        if(action.equals("contest")){
            if(s.owner==null||s.owner.equals(faction.id())||s.army==null||s.attacker!=null)return "Select an occupied enemy site with no existing raid.";
            if(!ready(army,d,5))return "Need at least five uncommitted troops to contest the site.";
            s.attacker=army.id;s.attackerFaction=faction.id();s.raidSeconds=0;order(army,s.x,s.z);mil.setDirty();d.setDirty();return "Raid ordered! On arrival, both armies contest the stockpile and mission progress.";
        }
        if(action.equals("abort")){
            if(!army.id.equals(s.army)||!faction.id().equals(s.owner))return "You do not control this operation.";
            if(s.attacker!=null)return "Cannot abort during an active raid.";
            release(army);s.army=null;s.owner=null;s.stage="ABANDONED";d.setDirty();mil.setDirty();return "Operation abandoned. All stockpiled resources remain at the site for rivals.";
        }
        if(action.equals("recalloutpost")){
            if(!"OUTPOST".equals(s.stage)||s.attacker!=null||!army.id.equals(s.army)||!faction.id().equals(s.owner))return "No uncontested friendly outpost garrison to withdraw.";
            release(army);s.army=null;s.owner=null;s.stage="ABANDONED";d.setDirty();mil.setDirty();return "Outpost abandoned and garrison returning home.";
        }
        return "Unknown operation action.";
    }
    private static boolean ready(MilitaryData.Army a,OperationsData d,int minimum){return a!=null&&!a.marching&&a.fielded==0&&a.strength>=minimum&&!d.committed(a.id);}
    public static String sendCaravan(ServerPlayer p,BlockPos table,UUID armyId,int destX,int destZ,String good,int amount){
        if(!WarMapNetwork.isAuthorizedControl(p,table))return "Stand near an authorized War Table or Kingdom Planner.";
        var faction=BarracksService.faction(p.serverLevel(),table);if(faction==null||!BarracksService.canManage(p,table))return "Faction military permission required.";
        var d=OperationsData.get(p.getServer());var mil=MilitaryData.get(p.getServer());var army=mil.armies.get(armyId);
        if(army==null||!army.faction.equals(faction.id())||!ready(army,d,5)||!at(army,table.getX(),table.getZ()))return "Station a free 5+ soldier army at this War Table.";
        if(!GOODS.containsKey(good)||amount!=16&&amount!=32)return "Choose a shipment of 16 or 32 valid resources.";
        String dim=KingdomData.dim(p.serverLevel());if(Math.hypot(destX-table.getX(),destZ-table.getZ())>RANGE)return "Caravan destination must be within 2048 blocks.";
        var land=KingdomData.get(p.getServer());var claim=land.claimAt(dim,destX>>4,destZ>>4);if(claim==null)return "Click a claimed kingdom or town destination on the map.";
        var t=land.townFor(claim);UUID recipient=t==null?Optional.ofNullable(land.ownerAt(dim,destX>>4,destZ>>4)).map(k->k.id).orElse(null):t.id;
        if(recipient==null)return "Destination faction not found.";
        // Only own or allied territory may receive shipments; do not hand free resources to enemies.
        boolean allowed=recipient.equals(faction.id());
        if(t!=null){var parent=t.parentKingdom;allowed|=parent!=null&&parent.equals(faction.id());}
        if(faction.type().equals("town")){var ownTown=land.towns.get(faction.id());if(ownTown!=null&&ownTown.parentKingdom!=null){allowed|=ownTown.parentKingdom.equals(recipient);if(t!=null)allowed|=ownTown.parentKingdom.equals(t.parentKingdom);}}
        if(!allowed)return "Destination must belong to your faction or an affiliated town/kingdom.";
        if(d.caravans.size()>=80)return "Too many active caravans on the server.";
        if(!PhysicalWarehouses.payLocal(p.serverLevel(),faction.id(),table,96,Map.of(good,amount)))return "Not enough "+good+" in the warehouse near your War Table.";
        OperationsData.Caravan c=new OperationsData.Caravan();c.faction=faction.id();c.destinationFaction=recipient;c.army=army.id;c.dim=dim;c.good=good;c.amount=amount;c.x=destX;c.z=destZ;c.locationX=army.locationX;c.locationZ=army.locationZ;
        d.caravans.put(c.id,c);order(army,c.x,c.z);mil.setDirty();d.setDirty();return "Caravan dispatched with "+amount+" "+good+". Rivals may intercept before delivery.";
    }
    public static void tick(MinecraftServer server){
        OperationsData d=OperationsData.get(server);MilitaryData military=MilitaryData.get(server);
        for(var s:d.sites.values()){
            var army=military.armies.get(s.army);var attacker=military.armies.get(s.attacker);
            if(s.attacker!=null&&attacker==null){s.attacker=null;s.attackerFaction=null;s.raidSeconds=0;d.setDirty();}
            if(s.army!=null&&army==null){s.army=null;s.owner=null;s.stage="ABANDONED";d.setDirty();}
            if(attacker!=null&&at(attacker,s.x,s.z)){
                s.raidSeconds++;if(s.raidSeconds>=30){
                    // Server-calculated deterministic combat, with genuine casualties and no reset of stolen progress.
                    int defend=army==null?0:army.strength+("OUTPOST".equals(s.stage)&&EconomyService.funded(server,s.owner)?Math.max(2,army.strength/3):0);
                    boolean win=attacker.strength>defend;
                    if(win){if(army!=null){army.strength=Math.max(0,army.strength-Math.max(1,army.strength/3));release(army);}
                        attacker.strength=Math.max(1,attacker.strength-Math.max(1,attacker.strength/5));
                        s.owner=s.attackerFaction;s.army=attacker.id;
                        if(s.stage.equals("SCOUT"))s.stage="OPERATE";if(s.stage.equals("BUILD_OUTPOST"))s.stage="OUTPOST";
                    }else{attacker.strength=Math.max(0,attacker.strength-Math.max(1,attacker.strength/4));release(attacker);if(army!=null)army.strength=Math.max(0,army.strength-Math.max(1,army.strength/10));}
                    s.attacker=null;s.attackerFaction=null;s.raidSeconds=0;military.setDirty();d.setDirty();
                }
            }
            if(army==null||s.attacker!=null)continue;
            if(s.stage.equals("OUTPOST")&&at(army,s.x,s.z)&&EconomyService.funded(server,s.owner)){
                // Once per minute, not per tick: live garrisons work a mine, farm, forest or quarry.
                s.passiveSeconds++;
                if(s.passiveSeconds>=60){
                    s.passiveSeconds=0;
                    int produced=producedPerMinute(s.type);
                    // Hold undelivered output at this site; pause at the cap when warehouse is full.
                    s.stock=Math.min(256,s.stock+Math.min(produced,Math.max(0,256-s.stock)));
                    d.setDirty();
                }
                if(s.stock>0){
                    var w=server.getLevel(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION,new net.minecraft.resources.ResourceLocation(s.dim)));
                    if(w!=null){int arrived=PhysicalWarehouses.deliver(w,s.owner,new BlockPos(s.x,w.getSeaLevel(),s.z),s.good(),s.stock);
                        if(arrived>0){ProductionData.record(server,s.owner,s.good(),arrived);s.stock-=arrived;d.setDirty();}
                    }
                }
            }
            if(s.stage.equals("SCOUT")&&at(army,s.x,s.z)){
                s.workingSeconds++;if(s.workingSeconds>=25){s.scouted.add(s.owner);release(army);s.army=null;s.owner=null;s.stage="IDLE";s.workingSeconds=0;d.setDirty();military.setDirty();}
            }
            if(s.stage.equals("BUILD_OUTPOST")&&at(army,s.x,s.z)){
                if(!EconomyService.funded(server,s.owner))continue;
                s.workingSeconds++;if(s.workingSeconds>=45){s.stage="OUTPOST";s.workingSeconds=0;d.setDirty();}
            }
            if(s.stage.equals("OPERATE")&&at(army,s.x,s.z)&&EconomyService.funded(server,s.owner)){
                // Extraction progress belongs to the SITE, never an army: captures inherit it unchanged.
                s.workingSeconds++;int before=s.progress;
                s.progress=Math.min(100,(int)(100L*s.workingSeconds/s.duration()));
                s.stock=Math.max(s.stock,(int)((long)s.total*s.progress/100));
                if(s.progress!=before)d.setDirty();
                if(s.progress>=100){
                    OperationsData.Caravan cargo=new OperationsData.Caravan();cargo.dim=s.dim;cargo.good=s.good();cargo.amount=s.stock;cargo.army=army.id;cargo.faction=s.owner;cargo.destinationFaction=s.owner;cargo.locationX=army.locationX;cargo.locationZ=army.locationZ;cargo.x=army.origin.getX();cargo.z=army.origin.getZ();cargo.stage="RETURNING";
                    d.caravans.put(cargo.id,cargo);order(army,cargo.x,cargo.z);
                    s.stock=0;s.army=null;s.owner=null;s.stage="EXHAUSTED";d.setDirty();military.setDirty();
                }
            }
        }
        List<UUID> completed=new ArrayList<>();
        for(var c:d.caravans.values()){
            var army=military.armies.get(c.army);var attacker=military.armies.get(c.attacker);
            if(army==null){completed.add(c.id);continue;}
            c.locationX=army.locationX;c.locationZ=army.locationZ;
            if(c.amount>0&&attacker!=null&&Math.hypot(attacker.locationX-c.locationX,attacker.locationZ-c.locationZ)>=24){
                order(attacker,(int)c.locationX,(int)c.locationZ);military.setDirty();
            }
            if(c.amount>0&&attacker!=null&&Math.hypot(attacker.locationX-c.locationX,attacker.locationZ-c.locationZ)<24){
                c.raidSeconds++;if(c.raidSeconds>=30){
                    boolean win=attacker.strength>army.strength;
                    if(win){army.strength=Math.max(0,army.strength-Math.max(1,army.strength/3));release(army);attacker.strength=Math.max(1,attacker.strength-Math.max(1,attacker.strength/5));
                        c.faction=c.attackerFaction;c.destinationFaction=c.attackerFaction;c.army=attacker.id;c.stage="RETURNING";c.x=attacker.origin.getX();c.z=attacker.origin.getZ();order(attacker,c.x,c.z);
                    }else{attacker.strength=Math.max(0,attacker.strength-Math.max(1,attacker.strength/4));release(attacker);}
                    c.attacker=null;c.attackerFaction=null;c.raidSeconds=0;military.setDirty();d.setDirty();
                }
            }
            if(at(army,c.x,c.z)&&c.attacker==null){
                if(c.amount>0){
                    // Missions return physical cargo to the destination settlement.
                    // Full/missing warehouses hold the caravan until storage becomes available.
                    var world=server.getLevel(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION,new net.minecraft.resources.ResourceLocation(c.dim)));
                    int delivered=world==null?0:PhysicalWarehouses.deliver(world,c.destinationFaction,
                        new BlockPos(c.x,world.getSeaLevel(),c.z),c.good,c.amount);
                    if(delivered>0){c.amount-=delivered;d.setDirty();}
                    if(c.amount>0)continue;
                }
                if(c.stage.equals("OUTBOUND")){c.stage="GO_HOME";order(army,army.origin.getX(),army.origin.getZ());military.setDirty();d.setDirty();}
                else completed.add(c.id);
            }
            if(c.stage.equals("GO_HOME")&&at(army,army.origin.getX(),army.origin.getZ()))completed.add(c.id);
        }
        if(!completed.isEmpty()){for(UUID id:completed)d.caravans.remove(id);d.setDirty();}
    }
}
