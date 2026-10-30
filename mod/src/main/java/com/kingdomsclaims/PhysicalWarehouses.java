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
        var f=BarracksService.faction(w,p);return f!=null&&SharedStockpile.owner(w.getServer(),f.id()).equals(SharedStockpile.owner(w.getServer(),faction));
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
    public static int sharedCapacity(net.minecraft.server.MinecraftServer server,UUID faction){
        UUID owner=SharedStockpile.owner(server,faction);long capacity=1024;
        for(ServerLevel level:server.getAllLevels())for(var entry:WarehouseData.get(level).depots.entrySet()){
            BlockPos pos=BlockPos.of(entry.getKey());var d=entry.getValue();
            if(!owner.equals(SharedStockpile.owner(server,d.faction)))continue;
            // Keep cached storage capacity in unloaded chunks; validate loaded blocks and current ownership.
            if((level.hasChunkAt(pos)&&!core(level,pos))||!factionMatches(level,pos,d.faction))continue;
            int shelves=0;for(long id:d.shelves){BlockPos p=BlockPos.of(id);if((!level.hasChunkAt(p)||module(level,p))&&factionMatches(level,p,d.faction))shelves++;}
            capacity+=(long)(shelves+1)*576;
        }
        capacity+=RegisteredBuildings.get(server).warehouseCapacity(server,owner);
        return (int)Math.min(2_000_000_000L,capacity);
    }
    private static SharedStockpile stock(ServerLevel level){return SharedStockpile.get(level.getServer());}

    public static int capacity(ServerLevel w,BlockPos pos){return capacity(modules(w,pos,null).size());}
    private static WarehouseData.Depot data(ServerLevel w,BlockPos pos,UUID faction){
        if(!core(w,pos))return null;
        var owner=BarracksService.faction(w,pos);if(owner==null||!SharedStockpile.owner(w.getServer(),owner.id()).equals(SharedStockpile.owner(w.getServer(),faction)))return null;
        WarehouseData.Depot d=WarehouseData.get(w).ensure(pos,faction);
        if(d!=null)migrateOld(w,pos,d);
        return d;
    }
    public record InventoryView(int[] stock,int[] carried,int capacity,int used,int modules){}
    public static InventoryView view(ServerPlayer p,BlockPos pos,UUID faction){
        int[] totals=new int[OperationsData.GOODS.length],carried=new int[totals.length];
        for(int i=0;i<totals.length;i++){
            totals[i]=stock(p.serverLevel()).amount(faction,OperationsData.GOODS[i]);
            carried[i]=EconomyService.carried(p,OperationsData.GOODS[i]);
        }
        return new InventoryView(totals,carried,sharedCapacity(p.getServer(),faction),stock(p.serverLevel()).used(faction),modules(p.serverLevel(),pos,null).size());
    }

        /** Nearby storage only: no forced chunk loads or global resource pooling. */
    public static BlockPos nearest(ServerLevel w,UUID faction,BlockPos target,int maxDistance){
        BlockPos found=null;double best=(double)maxDistance*maxDistance;
        for(var e:WarehouseData.get(w).depots.entrySet()){
            if(!SharedStockpile.owner(w.getServer(),e.getValue().faction).equals(SharedStockpile.owner(w.getServer(),faction)))continue;
            BlockPos pos=BlockPos.of(e.getKey());
            double distance=(double)(pos.getX()-target.getX())*(pos.getX()-target.getX())+
                (double)(pos.getZ()-target.getZ())*(pos.getZ()-target.getZ());
            if(distance<=best&&core(w,pos)&&factionMatches(w,pos,e.getValue().faction)){best=distance;found=pos;}
        }
        return found;
    }
    /** Available stock is local to one loaded warehouse: no shared magic treasury. */
    public static int localStock(ServerLevel w,UUID faction,BlockPos at,int radius,String good){
        return stock(w).amount(faction,good);
    }
    public static boolean payLocal(ServerLevel w,UUID faction,BlockPos at,int radius,Map<String,Integer> needed){
        return stock(w).pay(faction,needed);
    }
    public static boolean canPayLocal(ServerLevel w,UUID faction,BlockPos at,int radius,Map<String,Integer> needed){
        return stock(w).canPay(faction,needed);
    }

        /** Pick one loaded, sufficiently stocked warehouse near the banner.
     * The old nearest-only choice ignored a stocked warehouse just a few blocks farther away. */
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
        return SharedStockpile.get(server).pay(faction,costs);
    }
    public static boolean canPayHeadquarters(net.minecraft.server.MinecraftServer server,UUID faction,Map<String,Integer> costs){
        return SharedStockpile.get(server).canPay(faction,costs);
    }
    /** A gold ingot equals nine nuggets; all resource payments are settled against one kingdom balance. */
    public static boolean canPayUpkeep(net.minecraft.server.MinecraftServer server,UUID faction,Map<String,Integer> goods,int wageNuggets){
        if(wageNuggets<0)return false;
        SharedStockpile s=SharedStockpile.get(server);
        if(!s.canPay(faction,goods)||9L*s.amount(faction,"GOLD")+s.amount(faction,"GOLD_NUGGETS")<wageNuggets)return false;
        var payroll=StockpileMath.goldPayment(s.amount(faction,"GOLD"),s.amount(faction,"GOLD_NUGGETS"),wageNuggets);
        if(payroll==null)return false;int nuggets=payroll.get("GOLD_NUGGETS"),ingots=payroll.get("GOLD"),change=payroll.get("CHANGE");
        return (long)s.amount(faction,"GOLD_NUGGETS")-nuggets+change<=Integer.MAX_VALUE;
    }
    public static boolean payUpkeep(net.minecraft.server.MinecraftServer server,UUID faction,Map<String,Integer> goods,int wageNuggets){
        if(!canPayUpkeep(server,faction,goods,wageNuggets))return false;
        SharedStockpile s=SharedStockpile.get(server);
        var payroll=StockpileMath.goldPayment(s.amount(faction,"GOLD"),s.amount(faction,"GOLD_NUGGETS"),wageNuggets);
        if(payroll==null)return false;int nuggets=payroll.get("GOLD_NUGGETS"),ingots=payroll.get("GOLD"),change=payroll.get("CHANGE");
        Map<String,Integer> payment=new HashMap<>(goods);payment.merge("GOLD",ingots,Integer::sum);payment.merge("GOLD_NUGGETS",nuggets,Integer::sum);
        return s.payWithGoldChange(faction,payment,change);
    }

    /** Read only loaded warehouse cores, bounded by the player's actual built warehouses. */
    public static int factionStock(net.minecraft.server.MinecraftServer server,UUID faction,String good){
        return SharedStockpile.get(server).amount(faction,good);
    }

        /** Preserve old NBT balances and migrate in small, capacity-bounded portions. Never duplicate. */
    private static void importLegacy(ServerLevel w,BlockPos pos,WarehouseData.Depot d){}
    public static void importHeadquartersGold(net.minecraft.server.MinecraftServer server,UUID faction){}
    public static void importAvailable(ServerLevel w,UUID faction,BlockPos pos){}
    public static void importAll(net.minecraft.server.MinecraftServer server){}
    private static boolean transferAllowed(ServerPlayer p,UUID faction,BlockPos near){
        if(p.distanceToSqr(near.getX()+.5,near.getY()+.5,near.getZ()+.5)>96*96)return false;
        var land=KingdomData.get(p.server);var k=land.kingdoms.get(SharedStockpile.owner(p.server,faction));var t=land.towns.get(faction);
        return k!=null&&k.allowed(p.getUUID(),KingdomData.STORAGE)||t!=null&&t.allowed(p.getUUID(),KingdomData.STORAGE);
    }
    /** Physical inventory transaction at the operating site (e.g. War Table). */
    public static int storeFromPlayer(ServerPlayer player,UUID faction,BlockPos near,String good,int amount){
        Item item=EconomyService.goodsItem(good);if(item==null||amount<=0||!transferAllowed(player,faction,near))return 0;
        // Physical deposits must be made at a genuine warehouse core.
        BlockPos pos=nearest(player.serverLevel(),faction,near,96);
        if(pos==null)return 0;
        int available=EconomyService.carried(player,good);
        int accepted=stock(player.serverLevel()).add(faction,good,Math.min(amount,available),sharedCapacity(player.getServer(),faction));
        if(accepted<=0)return 0;
        int left=accepted;
        for(ItemStack stack:player.getInventory().items)if(stack.is(item)&&!stack.hasTag()&&left>0){int n=Math.min(left,stack.getCount());stack.shrink(n);left-=n;}
        player.getInventory().setChanged();return accepted;
    }
    public static int takeToPlayer(ServerPlayer player,UUID faction,BlockPos near,String good,int amount){
        Item item=EconomyService.goodsItem(good);if(item==null||amount<=0||!transferAllowed(player,faction,near))return 0;
        if(nearest(player.serverLevel(),faction,near,96)==null)return 0;
        int n=Math.min(amount,Math.min(stock(player.serverLevel()).amount(faction,good),playerRoom(player.getInventory(),item)));
        if(n<=0||!stock(player.serverLevel()).pay(faction,Map.of(good,n)))return 0;
        give(player.getInventory(),item,n);return n;
    }

        public static int put(ServerLevel world,UUID faction,BlockPos pos,String good,int amount){return deliver(world,faction,pos,good,amount);}
    /** Site production: count active owned operations, no block scan and no entity renderer. */
    public static int[] rates(ServerLevel world,UUID faction,BlockPos at){return RegisteredBuildings.get(world.getServer()).rates(world.getServer(),faction);}
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
    public static int materialUnits(Item item){
        String n=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath();
        return Set.of("iron_block","gold_block","coal_block","emerald_block","hay_block").contains(n)?9:1;
    }
    /** Atomic on the server thread: verify stock/inventory BEFORE withdrawing anything. */
    public static String payConstruction(ServerPlayer player,UUID faction,BlockPos anchor,Map<Item,Integer> costs){
        SharedStockpile s=stock(player.serverLevel());Map<Item,Integer> personal=new LinkedHashMap<>();Map<String,Integer> shared=new HashMap<>();
        for(var entry:costs.entrySet()){
            String good=buildingGood(entry.getKey());int unit=materialUnits(entry.getKey());int required=Math.multiplyExact(entry.getValue(),unit);
            int fromShared=good==null?0:required;
            if(fromShared>0)shared.merge(good,fromShared,Integer::sum);
            if(required>fromShared)personal.merge(entry.getKey(),(required-fromShared+unit-1)/unit,Integer::sum);
        }
        List<String> missing=new ArrayList<>();
        for(var e:personal.entrySet()){
            int count=0;for(ItemStack stack:player.getInventory().items)if(stack.is(e.getKey()))count+=stack.getCount();
            if(count<e.getValue())missing.add(e.getKey().getDescription().getString()+" "+count+"/"+e.getValue());
        }
        if(!missing.isEmpty())return "Missing: "+String.join(", ",missing.subList(0,Math.min(4,missing.size())));
        if(!s.pay(faction,shared))return "Not enough kingdom resources for this design. See its material requirements.";
        for(var e:personal.entrySet()){
            int left=e.getValue();for(ItemStack stack:player.getInventory().items)if(stack.is(e.getKey())&&left>0){int used=Math.min(left,stack.getCount());stack.shrink(used);left-=used;}
        }
        player.getInventory().setChanged();return null;
    }

        /** Return cargo to the closest loaded warehouse near its destination.
     * Partial deliveries retain remaining cargo; no fallback to an invisible ledger. */
    public static int deliver(ServerLevel w,UUID faction,BlockPos destination,String good,int requested){
        return faction==null||requested<=0?0:stock(w).add(faction,good,requested,sharedCapacity(w.getServer(),faction));
    }

        private static int playerRoom(Inventory inv,Item item){
        int room=0;for(ItemStack s:inv.items){
            if(s.isEmpty())room+=item.getDefaultInstance().getMaxStackSize();
            else if(s.is(item)&&!s.hasTag())room+=Math.max(0,s.getMaxStackSize()-s.getCount());
        }return room;
    }
    private static void give(Inventory inv,Item item,int n){
        for(int pass=0;pass<2&&n>0;pass++)for(int i=0;i<inv.items.size()&&n>0;i++){
            ItemStack s=inv.items.get(i);if(pass==0&&(!s.is(item)||s.hasTag())||pass==1&&!s.isEmpty())continue;
            int take=Math.min(n,item.getDefaultInstance().getMaxStackSize()-(s.isEmpty()?0:s.getCount()));
            if(take<=0)continue;if(s.isEmpty())inv.items.set(i,new ItemStack(item,take));else s.grow(take);n-=take;
        }inv.setChanged();
    }
    private static void drawFill(ServerLevel w,BlockPos pos,WarehouseData.Depot depot,BlockPos excluded){
        List<BlockPos> positions=modules(w,pos,excluded);
        int n=stock(w).used(depot.faction),per=WarehouseData.SLOTS_PER_BLOCK*WarehouseData.ITEMS_PER_SLOT;
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
        if(data(p.serverLevel(),pos,faction)==null)return "This warehouse does not belong to your kingdom.";
        if("deposit".equals(action)){
            int moved=storeFromPlayer(p,faction,pos,good,64);
            return moved>0?"Deposited "+moved+" "+good.toLowerCase(Locale.ROOT)+".":"No items carried, or kingdom storage is full.";
        }
        if("withdraw".equals(action)){
            int moved=takeToPlayer(p,faction,pos,good,64);
            return moved>0?"Withdrew "+moved+" "+good.toLowerCase(Locale.ROOT)+".":"Nothing available or inventory full.";
        }
        return "Unknown action.";
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

            WarehouseData.get(w).ensure(pos,faction.id());refresh(w,pos);
        }else if(e.getPlacedBlock().is(KingdomsClaims.STORAGE_EXPANSION.get())){
            var faction=BarracksService.faction(w,pos);
            if(faction==null){e.setCanceled(true);feedback(e,"Shelves must be inside a claimed territory.");return;}
            BlockPos origin=candidateCore(w,pos,faction.id());
            if(origin==null)return; // Standalone shelves can be counted by a registered warehouse.
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
                if(!d.shelves.isEmpty()){
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
