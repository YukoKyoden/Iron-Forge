package com.kingdomsclaims;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Local, modular stockpile. Connectivity is evaluated only on placement, removal or UI actions.
 * No ticking, no chest scans and no block entity renderers. */
public final class PhysicalWarehouses {
    private PhysicalWarehouses(){}
    // Bounded, event-driven local storage graph. No world-wide, ticking or forced chunk scans.
    public static final int MAX_LINK_DISTANCE=10, MAX_CORE_RADIUS=50, MIN_CORE_SPACING=128;
    private static final int LINK_DISTANCE_SQ=MAX_LINK_DISTANCE*MAX_LINK_DISTANCE;
    private static final int CORE_RADIUS_SQ=MAX_CORE_RADIUS*MAX_CORE_RADIUS;
    private static boolean core(ServerLevel level,BlockPos p){return level.hasChunkAt(p)&&level.getBlockState(p).is(KingdomsClaims.SUPPLY_DEPOT.get());}
    private static boolean module(ServerLevel level,BlockPos p){return level.hasChunkAt(p)&&level.getBlockState(p).is(KingdomsClaims.STORAGE_EXPANSION.get());}
    private static boolean within(BlockPos a,BlockPos b,int distanceSq){return a.distSqr(b)<=distanceSq;}
    private static boolean factionMatches(ServerLevel w,BlockPos p,UUID faction){
        var f=BarracksService.faction(w,p);return f!=null&&f.id().equals(faction);
    }
    /** One-time, adjacent-only adoption of old v0.4.19/20 expansions. Never loads chunks. */
    private static void migrateOld(ServerLevel w,BlockPos origin,WarehouseData.Depot d){
        if(d.shelvesMigrated)return;
        d.shelvesMigrated=true;
        if(core(w,origin)){
            ArrayDeque<BlockPos> queue=new ArrayDeque<>();HashSet<Long> seen=new HashSet<>();
            queue.add(origin);seen.add(origin.asLong());
            while(!queue.isEmpty()&&d.shelves.size()<WarehouseData.MAX_MODULES){
                BlockPos here=queue.removeFirst();
                for(Direction dir:Direction.values()){
                    BlockPos n=here.relative(dir);
                    if(!within(origin,n,CORE_RADIUS_SQ)||!seen.add(n.asLong())||!module(w,n))continue;
                    if(!factionMatches(w,n,d.faction))continue;
                    d.shelves.add(n.asLong());queue.add(n);
                    if(d.shelves.size()>=WarehouseData.MAX_MODULES)break;
                }
            }
        }
        WarehouseData.get(w).setDirty();
    }
    /** A shelf can be far from the core as long as every hop is <= 10 blocks and all are within 50. */
    private static boolean reachesCore(BlockPos origin,Collection<Long> shelves){
        if(shelves.isEmpty())return true;
        HashSet<Long> connected=new HashSet<>();ArrayDeque<BlockPos> q=new ArrayDeque<>();
        q.add(origin);
        while(!q.isEmpty()){
            BlockPos p=q.removeFirst();
            for(long id:shelves){
                if(connected.contains(id))continue;
                BlockPos n=BlockPos.of(id);
                if(within(origin,n,CORE_RADIUS_SQ)&&within(p,n,LINK_DISTANCE_SQ)){
                    connected.add(id);q.addLast(n);
                }
            }
        }
        return connected.size()==shelves.size();
    }
    private static boolean canAttach(BlockPos origin,Collection<Long> shelves,BlockPos candidate){
        if(!within(origin,candidate,CORE_RADIUS_SQ))return false;
        if(within(origin,candidate,LINK_DISTANCE_SQ))return true;
        for(long id:shelves)if(within(BlockPos.of(id),candidate,LINK_DISTANCE_SQ))return true;
        return false;
    }
    /** Only indexed, active warehouses are searched. No chunk loading or spatial world scans. */
    public static BlockPos findCore(ServerLevel w,BlockPos at){
        if(core(w,at))return at;
        WarehouseData data=WarehouseData.get(w);
        for(var entry:data.depots.entrySet()){
            BlockPos origin=BlockPos.of(entry.getKey());
            if(entry.getValue().shelves.contains(at.asLong())&&core(w,origin))return origin;
        }
        return null;
    }
    private static List<BlockPos> modules(ServerLevel w,BlockPos origin,BlockPos excluded){
        if(!core(w,origin)||origin.equals(excluded))return List.of();
        WarehouseData.Depot d=WarehouseData.get(w).at(origin);
        if(d==null)return List.of();
        migrateOld(w,origin,d);
        List<BlockPos> positions=new ArrayList<>();
        for(long id:d.shelves)if(excluded==null||id!=excluded.asLong())positions.add(BlockPos.of(id));
        positions.sort(Comparator.comparingLong(BlockPos::asLong));
        return positions;
    }
    private static BlockPos candidateCore(ServerLevel w,BlockPos pos,UUID faction){
        WarehouseData data=WarehouseData.get(w);
        BlockPos best=null;double distance=Double.MAX_VALUE;
        for(var entry:data.depots.entrySet()){
            if(!entry.getValue().faction.equals(faction))continue;
            BlockPos origin=BlockPos.of(entry.getKey());
            if(!core(w,origin)||entry.getValue().shelves.size()>=WarehouseData.MAX_MODULES)continue;
            migrateOld(w,origin,entry.getValue());
            if(!canAttach(origin,entry.getValue().shelves,pos))continue;
            if(!reachesCore(origin,entry.getValue().shelves))continue;
            double dist=origin.distSqr(pos);
            if(dist<distance){distance=dist;best=origin;}
        }
        return best;
    }
    private static boolean coreTooNear(ServerLevel w,BlockPos pos){
        for(var entry:WarehouseData.get(w).depots.entrySet()){
            BlockPos other=BlockPos.of(entry.getKey());
            if(other.equals(pos))continue;
            // Horizontal spacing prevents stacking cores in towers to defeat density limit.
            long x=(long)other.getX()-pos.getX(),z=(long)other.getZ()-pos.getZ();
            if(x*x+z*z<(long)MIN_CORE_SPACING*MIN_CORE_SPACING)return true;
        }
        return false;
    }
    private static int capacity(int count){return (count+1)*WarehouseData.SLOTS_PER_BLOCK*WarehouseData.ITEMS_PER_SLOT;}
    public static int capacity(ServerLevel w,BlockPos pos){return capacity(modules(w,pos,null).size());}
    private static WarehouseData.Depot data(ServerLevel w,BlockPos pos,UUID faction){
        if(!core(w,pos))return null;
        var owner=BarracksService.faction(w,pos);if(owner==null||!owner.id().equals(faction))return null;
        WarehouseData.Depot d=WarehouseData.get(w).ensure(pos,faction);
        if(d!=null)migrateOld(w,pos,d);
        return d;
    }
    public record InventoryView(int[] stock,int[] carried,int capacity,int used,int modules){}
    public static InventoryView view(ServerPlayer p,BlockPos pos,UUID faction){
        WarehouseData.Depot depot=data(p.serverLevel(),pos,faction);
        int[] stock=new int[OperationsData.GOODS.length],carried=new int[stock.length];
        if(depot!=null)importLegacy(p.serverLevel(),pos,depot);
        for(int i=0;i<stock.length;i++){
            stock[i]=depot==null?0:depot.count(OperationsData.GOODS[i]);
            carried[i]=EconomyService.carried(p,OperationsData.GOODS[i]);
        }
        int count=modules(p.serverLevel(),pos,null).size();return new InventoryView(stock,carried,capacity(count),depot==null?0:depot.total(),count);
    }
    /** Nearby storage only: no forced chunk loads or global resource pooling. */
    public static BlockPos nearest(ServerLevel w,UUID faction,BlockPos target,int maxDistance){
        BlockPos found=null;double best=(double)maxDistance*maxDistance;
        for(var e:WarehouseData.get(w).depots.entrySet()){
            if(!e.getValue().faction.equals(faction))continue;
            BlockPos pos=BlockPos.of(e.getKey());
            double distance=(double)(pos.getX()-target.getX())*(pos.getX()-target.getX())+
                (double)(pos.getZ()-target.getZ())*(pos.getZ()-target.getZ());
            if(distance<=best&&core(w,pos)){best=distance;found=pos;}
        }
        return found;
    }
    /** Available stock is local to one loaded warehouse: no shared magic treasury. */
    public static int localStock(ServerLevel w,UUID faction,BlockPos at,int radius,String good){
        BlockPos pos=nearest(w,faction,at,radius);
        WarehouseData.Depot d=pos==null?null:data(w,pos,faction);
        if(d!=null)importLegacy(w,pos,d);
        return d==null?0:d.count(good);
    }
    /** Material bundles are checked and deducted together on the server thread. */
    public static boolean payLocal(ServerLevel w,UUID faction,BlockPos at,int radius,Map<String,Integer> needed){
        BlockPos pos=nearest(w,faction,at,radius);
        WarehouseData.Depot d=pos==null?null:data(w,pos,faction);
        if(d==null)return needed.values().stream().allMatch(v->v<=0);
        importLegacy(w,pos,d);
        for(var e:needed.entrySet())if(e.getValue()<0||d.count(e.getKey())<e.getValue())return false;
        boolean changed=false;
        for(var e:needed.entrySet())if(e.getValue()>0){d.goods.put(e.getKey(),d.count(e.getKey())-e.getValue());changed=true;}
        if(changed){WarehouseData.get(w).setDirty();drawFill(w,pos,d,null);}
        return true;
    }
    public static boolean canPayLocal(ServerLevel w,UUID faction,BlockPos at,int radius,Map<String,Integer> needed){
        BlockPos pos=nearest(w,faction,at,radius);
        WarehouseData.Depot d=pos==null?null:data(w,pos,faction);
        if(d==null)return needed.values().stream().allMatch(v->v<=0);
        importLegacy(w,pos,d);
        for(var e:needed.entrySet())if(e.getValue()<0||d.count(e.getKey())<e.getValue())return false;
        return true;
    }
    /** Pick one loaded, sufficiently stocked warehouse near the banner.
     * The old nearest-only choice ignored a stocked warehouse just a few blocks farther away. */
    private static BlockPos stockedHeadquartersCore(ServerLevel w,UUID faction,BlockPos banner,Map<String,Integer> costs){
        BlockPos best=null;double bestDist=192.0*192.0;
        for(var e:WarehouseData.get(w).depots.entrySet()){
            if(!faction.equals(e.getValue().faction))continue;
            BlockPos pos=BlockPos.of(e.getKey());
            double dx=(double)pos.getX()-banner.getX(),dz=(double)pos.getZ()-banner.getZ();
            double distance=dx*dx+dz*dz;
            if(distance>bestDist||!core(w,pos))continue;
            WarehouseData.Depot depot=data(w,pos,faction);
            if(depot==null)continue;
            importLegacy(w,pos,depot);
            boolean enough=true;
            for(var cost:costs.entrySet())if(cost.getValue()<0||depot.count(cost.getKey())<cost.getValue()){
                enough=false;break;
            }
            if(enough){best=pos;bestDist=distance;}
        }
        return best;
    }
    public static boolean hasHeadquartersWarehouse(net.minecraft.server.MinecraftServer server,UUID faction){
        Headquarters h=headquarters(server,faction);
        return h!=null&&nearest(h.world(),faction,h.banner(),192)!=null;
    }
    /** Primary warehouse must be near the settlement's banner; distance cannot be bypassed. */
    public static record Headquarters(ServerLevel world,BlockPos banner){}
    public static Headquarters headquarters(net.minecraft.server.MinecraftServer server,UUID faction){
        KingdomData land=KingdomData.get(server);
        KingdomData.Banner banner=land.towns.containsKey(faction)?land.townBanner(faction):land.capital(faction);
        if(banner==null)return null;
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> key=net.minecraft.resources.ResourceKey.create(
            net.minecraft.core.registries.Registries.DIMENSION,new net.minecraft.resources.ResourceLocation(banner.dimension));
        ServerLevel level=server.getLevel(key);
        return level==null?null:new Headquarters(level,banner.pos);
    }
    public static boolean payHeadquarters(net.minecraft.server.MinecraftServer server,UUID faction,Map<String,Integer> costs){
        Headquarters h=headquarters(server,faction);
        if(h==null)return false;
        BlockPos stocked=stockedHeadquartersCore(h.world(),faction,h.banner(),costs);
        return stocked!=null&&payLocal(h.world(),faction,stocked,0,costs);
    }
    public static boolean canPayHeadquarters(net.minecraft.server.MinecraftServer server,UUID faction,Map<String,Integer> costs){
        Headquarters h=headquarters(server,faction);
        return h!=null&&stockedHeadquartersCore(h.world(),faction,h.banner(),costs)!=null;
    }
    /** Gold is stored as physical ingots and nuggets; one ingot equals nine nuggets.
     * Materials and gold payroll must be in the SAME nearby warehouse. */
    private static BlockPos upkeepCore(net.minecraft.server.MinecraftServer server,UUID faction,Map<String,Integer> goods,int wageNuggets){
        Headquarters h=headquarters(server,faction);
        if(h==null||wageNuggets<0)return null;
        ServerLevel w=h.world();BlockPos found=null;double best=192.0*192.0;
        for(var e:WarehouseData.get(w).depots.entrySet()){
            if(!faction.equals(e.getValue().faction))continue;
            BlockPos p=BlockPos.of(e.getKey());
            double dx=p.getX()-h.banner().getX(),dz=p.getZ()-h.banner().getZ(),sq=dx*dx+dz*dz;
            if(sq>best||!core(w,p))continue;
            WarehouseData.Depot d=data(w,p,faction);if(d==null)continue;
            importLegacy(w,p,d);
            boolean enough=true;
            for(var cost:goods.entrySet())if(cost.getValue()<0||d.count(cost.getKey())<cost.getValue()){enough=false;break;}
            if(!enough)continue;
            long available=9L*d.count("GOLD")+d.count("GOLD_NUGGETS");
            if(available<wageNuggets)continue;
            int spentNuggets=Math.min(wageNuggets,d.count("GOLD_NUGGETS"));
            int spentIngots=(wageNuggets-spentNuggets+8)/9;
            int change=spentIngots*9-(wageNuggets-spentNuggets);
            int futureUsed=d.total()-spentNuggets-spentIngots+change;
            for(var cost:goods.entrySet())futureUsed-=cost.getValue();
            if(futureUsed>capacity(w,p))continue; // no item overflow when making change
            best=sq;found=p;
        }
        return found;
    }
    public static boolean canPayUpkeep(net.minecraft.server.MinecraftServer server,UUID faction,Map<String,Integer> goods,int wageNuggets){
        return upkeepCore(server,faction,goods,wageNuggets)!=null;
    }
    public static boolean payUpkeep(net.minecraft.server.MinecraftServer server,UUID faction,Map<String,Integer> goods,int wageNuggets){
        BlockPos p=upkeepCore(server,faction,goods,wageNuggets);
        if(p==null)return false;
        Headquarters h=headquarters(server,faction);
        if(h==null)return false;
        WarehouseData.Depot d=data(h.world(),p,faction);
        if(d==null)return false;
        int takeNuggets=Math.min(wageNuggets,d.count("GOLD_NUGGETS"));
        int takeIngots=(wageNuggets-takeNuggets+8)/9;
        int change=takeIngots*9-(wageNuggets-takeNuggets);
        for(var cost:goods.entrySet())if(cost.getValue()>0)d.goods.put(cost.getKey(),d.count(cost.getKey())-cost.getValue());
        d.goods.put("GOLD",d.count("GOLD")-takeIngots);
        d.goods.put("GOLD_NUGGETS",d.count("GOLD_NUGGETS")-takeNuggets+change);
        WarehouseData.get(h.world()).setDirty();drawFill(h.world(),p,d,null);
        return true;
    }
    /** Read only loaded warehouse cores, bounded by the player's actual built warehouses. */
    public static int factionStock(net.minecraft.server.MinecraftServer server,UUID faction,String good){
        long total=0;
        for(ServerLevel world:server.getAllLevels())for(var e:WarehouseData.get(world).depots.entrySet())
            if(e.getValue().faction.equals(faction)&&core(world,BlockPos.of(e.getKey())))total+=e.getValue().count(good);
        return (int)Math.min(Integer.MAX_VALUE,total);
    }
    /** Preserve old NBT balances and migrate in small, capacity-bounded portions. Never duplicate. */
    private static void importLegacy(ServerLevel w,BlockPos pos,WarehouseData.Depot d){
        int free=Math.max(0,capacity(w,pos)-d.total());if(free==0)return;
        boolean changed=false;
        KingdomData land=KingdomData.get(w.getServer());OperationsData old=OperationsData.get(w.getServer());
        for(String good:OperationsData.GOODS){
            if(free<=0)break;
            int carried=old.warehouse(d.faction,good);
            int move=Math.min(free,carried);
            if(move>0&&old.debit(d.faction,good,move)){
                d.goods.merge(good,move,Integer::sum);free-=move;changed=true;WarehouseData.get(w).setDirty();
            }
            if(!good.equals("IRON")&&!good.equals("WHEAT")||free<=0)continue;
            var kingdom=land.kingdoms.get(d.faction);var town=land.towns.get(d.faction);
            long stored=kingdom!=null?(good.equals("IRON")?kingdom.treasuryIron:kingdom.treasuryWheat):
                town!=null?(good.equals("IRON")?town.treasuryIron:town.treasuryWheat):0;
            int amount=(int)Math.min(free,Math.max(0,stored));
            if(amount>0){
                if(kingdom!=null){if(good.equals("IRON"))kingdom.treasuryIron-=amount;else kingdom.treasuryWheat-=amount;}
                else if(town!=null){if(good.equals("IRON"))town.treasuryIron-=amount;else town.treasuryWheat-=amount;}
                d.goods.merge(good,amount,Integer::sum);free-=amount;changed=true;
                land.setDirty();WarehouseData.get(w).setDirty();
            }
        }
        // The last virtual money is one-way migrated into REAL Minecraft gold items.
        // Exactly 9 old Crowns become one ingot; remaining Crowns become nuggets.
        DistrictData districts=DistrictData.get(w.getServer());
        DistrictData.Civics civics=districts.city(d.faction);
        long remainingCrowns=civics.crowns;
        long oldPending=civics.pendingTaxNuggets;
        long usable=Math.min(2_000_000_000L,remainingCrowns+oldPending);
        // Prefer older Crown balances first so that nothing can be spent twice.
        int ingots=(int)Math.min((long)free,usable/9);
        if(ingots>0){
            long value=9L*ingots;
            long fromCrowns=Math.min(remainingCrowns,value);
            remainingCrowns-=fromCrowns;oldPending-=value-fromCrowns;
            d.goods.merge("GOLD",ingots,Integer::sum);
            free-=ingots;changed=true;
        }
        // Transfer only whole nuggets after ingot packing; no lost fractional Crowns.
        int nuggets=(int)Math.min((long)free,Math.min(8L,remainingCrowns+oldPending));
        if(nuggets>0){
            long fromCrowns=Math.min(remainingCrowns,nuggets);
            remainingCrowns-=fromCrowns;oldPending-=nuggets-fromCrowns;
            d.goods.merge("GOLD_NUGGETS",nuggets,Integer::sum);
            changed=true;
        }
        if(remainingCrowns!=civics.crowns||oldPending!=civics.pendingTaxNuggets){
            civics.crowns=remainingCrowns;civics.pendingTaxNuggets=oldPending;
            districts.setDirty();WarehouseData.get(w).setDirty();
        }
        if(changed)drawFill(w,pos,d,null);
    }
    /** Migrate pending real gold tax rewards and old currency near this settlement's banner. */
    public static void importHeadquartersGold(net.minecraft.server.MinecraftServer server,UUID faction){
        Headquarters h=headquarters(server,faction);
        if(h==null)return;
        BlockPos warehouse=nearest(h.world(),faction,h.banner(),192);
        if(warehouse!=null){WarehouseData.Depot d=data(h.world(),warehouse,faction);if(d!=null)importLegacy(h.world(),warehouse,d);}
    }
    public static void importAvailable(ServerLevel w,UUID faction,BlockPos pos){
        WarehouseData.Depot d=data(w,pos,faction);if(d!=null)importLegacy(w,pos,d);
    }
    public static void importAll(net.minecraft.server.MinecraftServer server){
        for(ServerLevel world:server.getAllLevels())for(var e:new ArrayList<>(WarehouseData.get(world).depots.entrySet())){
            BlockPos pos=BlockPos.of(e.getKey());if(core(world,pos))importLegacy(world,pos,e.getValue());
        }
    }
    /** Physical inventory transaction at the operating site (e.g. War Table). */
    public static int storeFromPlayer(ServerPlayer player,UUID faction,BlockPos near,String good,int amount){
        Item item=EconomyService.goodsItem(good);if(item==null||amount<=0)return 0;
        BlockPos pos=nearest(player.serverLevel(),faction,near,96);
        WarehouseData.Depot d=pos==null?null:data(player.serverLevel(),pos,faction);
        if(d==null)return 0;importLegacy(player.serverLevel(),pos,d);
        int moved=Math.min(amount,Math.min(EconomyService.carried(player,good),Math.max(0,capacity(player.serverLevel(),pos)-d.total())));
        if(moved<=0)return 0;
        int left=moved;
        for(ItemStack stack:player.getInventory().items)if(stack.is(item)&&left>0){int take=Math.min(left,stack.getCount());stack.shrink(take);left-=take;}
        player.getInventory().setChanged();d.goods.merge(good,moved,Integer::sum);
        WarehouseData.get(player.serverLevel()).setDirty();drawFill(player.serverLevel(),pos,d,null);
        return moved;
    }
    public static int takeToPlayer(ServerPlayer player,UUID faction,BlockPos near,String good,int amount){
        Item item=EconomyService.goodsItem(good);if(item==null||amount<=0)return 0;
        BlockPos pos=nearest(player.serverLevel(),faction,near,96);
        WarehouseData.Depot d=pos==null?null:data(player.serverLevel(),pos,faction);
        if(d==null)return 0;importLegacy(player.serverLevel(),pos,d);
        int take=Math.min(amount,Math.min(d.count(good),playerRoom(player.getInventory(),item)));
        if(take<=0)return 0;
        d.goods.put(good,d.count(good)-take);WarehouseData.get(player.serverLevel()).setDirty();
        give(player.getInventory(),item,take);drawFill(player.serverLevel(),pos,d,null);
        return take;
    }
    public static int put(ServerLevel world,UUID faction,BlockPos pos,String good,int amount){return deliver(world,faction,pos,good,amount);}
    /** Site production: count active owned operations, no block scan and no entity renderer. */
    public static int[] rates(ServerLevel world,UUID faction,BlockPos at){
        int[] result=new int[OperationsData.GOODS.length];String dim=KingdomData.dim(world);
        for(var site:OperationsData.get(world.getServer()).sites.values()){
            if(!"OUTPOST".equals(site.stage)||!faction.equals(site.owner)||!dim.equals(site.dim))continue;
            if((long)(site.x-at.getX())*(site.x-at.getX())+(long)(site.z-at.getZ())*(site.z-at.getZ())>192L*192)continue;
            var army=MilitaryData.get(world.getServer()).armies.get(site.army);
            if(army==null||army.marching||Math.hypot(army.locationX-site.x,army.locationZ-site.z)>=2.5)continue;
            int amount=OperationsService.producedPerMinute(site.type);
            for(int i=0;i<OperationsData.GOODS.length;i++)if(OperationsData.GOODS[i].equals(site.good()))result[i]+=amount;
        }
        return result;
    }
    /** Convert only supported basic building blocks into physical material units.
     * Specialty items must still be carried: no phantom stocks or free item conversions. */
    public static String buildingGood(Item item){
        if(item==net.minecraft.world.item.Items.IRON_INGOT||item==net.minecraft.world.item.Items.IRON_BLOCK||item==net.minecraft.world.item.Items.IRON_BARS)return "IRON";
        if(item==net.minecraft.world.item.Items.EMERALD||item==net.minecraft.world.item.Items.EMERALD_BLOCK)return "EMERALDS";
        if(item==net.minecraft.world.item.Items.GOLD_INGOT||item==net.minecraft.world.item.Items.GOLD_BLOCK)return "GOLD";
        if(item==net.minecraft.world.item.Items.GOLD_NUGGET)return "GOLD_NUGGETS";
        if(item==net.minecraft.world.item.Items.COAL||item==net.minecraft.world.item.Items.COAL_BLOCK)return "COAL";
        if(item==net.minecraft.world.item.Items.WHEAT||item==net.minecraft.world.item.Items.HAY_BLOCK)return "WHEAT";
        String name=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath();
        if(name.endsWith("_log")||name.endsWith("_wood")||name.endsWith("_planks")||name.endsWith("_fence")||name.endsWith("_fence_gate")||name.endsWith("_door")||name.endsWith("_trapdoor"))return "LOGS";
        if(name.equals("stone")||name.equals("cobblestone")||name.equals("smooth_stone")||name.equals("stone_bricks")||name.equals("mossy_stone_bricks")||name.equals("deepslate")||name.equals("cobbled_deepslate")||name.equals("polished_deepslate")||name.equals("andesite")||name.equals("diorite")||name.equals("granite")||name.equals("tuff")||name.endsWith("_stone_bricks"))return "STONE";
        return null;
    }
    /** Atomic on the server thread: verify stock/inventory BEFORE withdrawing anything. */
    public static String payConstruction(ServerPlayer player,UUID faction,BlockPos anchor,Map<Item,Integer> costs){
        BlockPos nearby=nearest(player.serverLevel(),faction,anchor,96);
        WarehouseData.Depot depot=nearby==null?null:data(player.serverLevel(),nearby,faction);
        if(depot!=null)importLegacy(player.serverLevel(),nearby,depot);
        Map<Item,Integer> personal=new LinkedHashMap<>();Map<String,Integer> fromDepot=new HashMap<>();
        for(var entry:costs.entrySet()){
            Item item=entry.getKey();int need=entry.getValue();String good=buildingGood(item);
            int available=depot==null||good==null?0:Math.max(0,depot.count(good)-fromDepot.getOrDefault(good,0));
            int warehouseTake=Math.min(need,available);
            if(warehouseTake>0)fromDepot.merge(good,warehouseTake,Integer::sum);
            int carried=need-warehouseTake;
            if(carried>0)personal.merge(item,carried,Integer::sum);
        }
        List<String> missing=new ArrayList<>();
        for(var entry:personal.entrySet()){
            int have=0;for(ItemStack stack:player.getInventory().items)if(stack.is(entry.getKey()))have+=stack.getCount();
            if(have<entry.getValue())missing.add(entry.getKey().getDescription().getString()+" "+have+"/"+entry.getValue());
        }
        if(!missing.isEmpty())return "Missing from local warehouse / inventory: "+String.join(", ",missing.subList(0,Math.min(4,missing.size())));
        for(var entry:personal.entrySet()){
            int left=entry.getValue();for(ItemStack stack:player.getInventory().items)if(stack.is(entry.getKey())&&left>0){int used=Math.min(left,stack.getCount());stack.shrink(used);left-=used;}
        }
        player.getInventory().setChanged();
        if(depot!=null){
            for(var entry:fromDepot.entrySet())depot.goods.put(entry.getKey(),depot.count(entry.getKey())-entry.getValue());
            WarehouseData.get(player.serverLevel()).setDirty();drawFill(player.serverLevel(),nearby,depot,null);
        }
        return null;
    }
    /** Return cargo to the closest loaded warehouse near its destination.
     * Partial deliveries retain remaining cargo; no fallback to an invisible ledger. */
    public static int deliver(ServerLevel w,UUID faction,BlockPos destination,String good,int requested){
        if(faction==null||requested<=0)return 0;
        BlockPos nearby=nearest(w,faction,destination,192);
        WarehouseData.Depot depot=nearby==null?null:data(w,nearby,faction);
        if(depot==null||EconomyService.goodsItem(good)==null)return 0;
        importLegacy(w,nearby,depot);
        int add=Math.min(requested,Math.max(0,capacity(w,nearby)-depot.total()));
        if(add>0){depot.goods.merge(good,add,Integer::sum);WarehouseData.get(w).setDirty();drawFill(w,nearby,depot,null);}
        return add;
    }
    private static int playerRoom(Inventory inv,Item item){
        int room=0;for(ItemStack s:inv.items){
            if(s.isEmpty())room+=item.getDefaultInstance().getMaxStackSize();
            else if(s.is(item))room+=Math.max(0,s.getMaxStackSize()-s.getCount());
        }return room;
    }
    private static void give(Inventory inv,Item item,int n){
        for(int pass=0;pass<2&&n>0;pass++)for(int i=0;i<inv.items.size()&&n>0;i++){
            ItemStack s=inv.items.get(i);if(pass==0&&!s.is(item)||pass==1&&!s.isEmpty())continue;
            int take=Math.min(n,item.getDefaultInstance().getMaxStackSize()-(s.isEmpty()?0:s.getCount()));
            if(take<=0)continue;if(s.isEmpty())inv.items.set(i,new ItemStack(item,take));else s.grow(take);n-=take;
        }inv.setChanged();
    }
    private static void drawFill(ServerLevel w,BlockPos pos,WarehouseData.Depot depot,BlockPos excluded){
        List<BlockPos> positions=modules(w,pos,excluded);
        int n=depot.total(),per=WarehouseData.SLOTS_PER_BLOCK*WarehouseData.ITEMS_PER_SLOT;
        int remaining=Math.max(0,n-per); // Core handles first nine slots.
        for(BlockPos p:positions){
            int fill=Math.min(per,remaining);remaining=Math.max(0,remaining-per);
            int level=(int)Math.ceil(6.0*fill/per);
            if(!w.hasChunkAt(p))continue;
            var state=w.getBlockState(p);
            if(state.is(KingdomsClaims.STORAGE_EXPANSION.get())&&state.getValue(StorageExpansionBlock.FILL)!=level)
                w.setBlock(p,state.setValue(StorageExpansionBlock.FILL,level),2);
        }
    }
    private static void refresh(ServerLevel w,BlockPos core){
        if(core==null)return;
        var faction=BarracksService.faction(w,core);if(faction==null)return;
        WarehouseData.Depot d=data(w,core,faction.id());if(d!=null)drawFill(w,core,d,null);
    }
    /** Server-only, atomic with respect to other depot operations on the server thread. */
    public static String transfer(ServerPlayer p,BlockPos pos,UUID faction,String good,String action){
        Item item=EconomyService.goodsItem(good);if(item==null)return "Unknown resource.";
        WarehouseData.Depot d=data(p.serverLevel(),pos,faction);if(d==null)return "Warehouse no longer belongs to your settlement.";
        Inventory inv=p.getInventory();int free=capacity(p.serverLevel(),pos)-d.total();
        if("import".equals(action))return "Old stockpile imports are disabled. Use modular warehouse storage.";
        if("deposit".equals(action)){
            int batch=Math.min(Math.min(64,free),EconomyService.carried(p,good));
            if(batch<=0)return free<=0?"Warehouse full. Add shelves.":"No "+good.toLowerCase(Locale.ROOT)+" carried.";
            int left=batch;for(ItemStack s:inv.items)if(s.is(item)&&left>0){int taken=Math.min(left,s.getCount());s.shrink(taken);left-=taken;}
            if(left!=0)return "Inventory changed. Retry.";
            inv.setChanged();d.goods.merge(good,batch,Integer::sum);WarehouseData.get(p.serverLevel()).setDirty();drawFill(p.serverLevel(),pos,d,null);
            return "Stored "+batch+" "+good.toLowerCase(Locale.ROOT)+" here.";
        }
        if("withdraw".equals(action)){
            int batch=Math.min(64,d.count(good));if(batch==0)return "No "+good.toLowerCase(Locale.ROOT)+" stored here.";
            batch=Math.min(batch,playerRoom(inv,item));if(batch==0)return "Your inventory is full.";
            d.goods.put(good,d.count(good)-batch);WarehouseData.get(p.serverLevel()).setDirty();give(inv,item,batch);drawFill(p.serverLevel(),pos,d,null);
            return "Withdrew "+batch+" "+good.toLowerCase(Locale.ROOT)+" for transport.";
        }
        return "Unknown storage action.";
    }
    private static void feedback(BlockEvent.EntityPlaceEvent e,String msg){
        if(e.getEntity() instanceof ServerPlayer player)player.sendSystemMessage(Component.literal("[Warehouse] "+msg));
    }
    /** Placement validation and graph updates run on events only, never on ticks. */
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent e){
        if(e.isCanceled()||!(e.getLevel() instanceof ServerLevel w))return;
        BlockPos pos=e.getPos();
        if(e.getPlacedBlock().is(KingdomsClaims.SUPPLY_DEPOT.get())){
            var faction=BarracksService.faction(w,pos);
            if(faction==null){e.setCanceled(true);feedback(e,"Place Warehouse Cores inside your claims.");return;}
            if(coreTooNear(w,pos)){e.setCanceled(true);feedback(e,"Another Warehouse Core is within 128 blocks. Spread warehouses farther apart.");return;}
            WarehouseData.get(w).ensure(pos,faction.id());refresh(w,pos);
        }else if(e.getPlacedBlock().is(KingdomsClaims.STORAGE_EXPANSION.get())){
            var faction=BarracksService.faction(w,pos);
            if(faction==null){e.setCanceled(true);feedback(e,"Shelves must be inside a claimed territory.");return;}
            BlockPos origin=candidateCore(w,pos,faction.id());
            if(origin==null){e.setCanceled(true);feedback(e,"Shelf must link within 10 blocks of the core or another linked shelf, within 50 blocks of its core (100 shelves maximum).");return;}
            WarehouseData.Depot d=WarehouseData.get(w).at(origin);
            if(d==null||d.shelves.size()>=WarehouseData.MAX_MODULES){e.setCanceled(true);feedback(e,"Warehouse already has 100 shelves.");return;}
            d.shelves.add(pos.asLong());WarehouseData.get(w).setDirty();refresh(w,origin);
        }
    }
    /** Never delete stock or sever a chain by mining its connecting shelf. */
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent e){
        if(e.isCanceled()||!(e.getLevel() instanceof ServerLevel w))return;
        BlockPos pos=e.getPos();
        if(e.getState().is(KingdomsClaims.SUPPLY_DEPOT.get())){
            WarehouseData data=WarehouseData.get(w);WarehouseData.Depot d=data.at(pos);
            if(d!=null){
                migrateOld(w,pos,d);
                if(d.total()>0||!d.shelves.isEmpty()){
                    e.setCanceled(true);e.getPlayer().displayClientMessage(Component.literal("[Warehouse] Empty this core and remove every connected shelf first."),true);return;
                }
                data.depots.remove(pos.asLong());data.setDirty();
            }
        }else if(e.getState().is(KingdomsClaims.STORAGE_EXPANSION.get())){
            BlockPos origin=findCore(w,pos);if(origin==null)return;
            WarehouseData data=WarehouseData.get(w);WarehouseData.Depot d=data.at(origin);
            if(d==null)return;
            migrateOld(w,origin,d);
            LinkedHashSet<Long> after=new LinkedHashSet<>(d.shelves);after.remove(pos.asLong());
            if(!reachesCore(origin,after)){
                e.setCanceled(true);e.getPlayer().displayClientMessage(Component.literal("[Warehouse] Remove the outer shelves first; this shelf connects other shelves."),true);return;
            }
            if(d.total()>capacity(after.size())){
                e.setCanceled(true);e.getPlayer().displayClientMessage(Component.literal("[Warehouse] Too many stored resources; withdraw before removing this shelf."),true);return;
            }
            d.shelves.remove(pos.asLong());data.setDirty();drawFill(w,origin,d,pos);
        }
    }
}
