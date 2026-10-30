package com.kingdomsclaims;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;

/** Physical gold-backed kingdom economy and persistent five-minute settlement upkeep. */
public final class EconomyService {
    private EconomyService(){}
    public static final int INTERVAL_SECONDS=300;
    public static final class Ledger extends SavedData {
        public int seconds=0;
        public final Map<UUID,Integer> successfulPayments=new HashMap<>(), missedPayments=new HashMap<>();
        public final Map<UUID,Boolean> funded=new HashMap<>();
        /** Per-settlement billing clock. Zero means an unpaid cycle may be settled immediately. */
        public final Map<UUID,Integer> nextPaymentSeconds=new HashMap<>();
        public static Ledger get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(Ledger::load,Ledger::new,"kingdomsclaims_economy");}
        static Ledger load(CompoundTag tag){Ledger d=new Ledger();d.seconds=Math.max(0,Math.min(299,tag.getInt("Seconds")));
            for(Tag e:tag.getList("Factions",Tag.TAG_COMPOUND)){
                CompoundTag t=(CompoundTag)e;if(!t.hasUUID("Id"))continue;UUID id=t.getUUID("Id");
                d.funded.put(id,t.getBoolean("Funded"));
                // Old saves used one global timer. Preserve its remaining time on migration.
                d.nextPaymentSeconds.put(id,t.contains("NextPaymentSeconds",Tag.TAG_INT)?
                    Math.max(0,Math.min(INTERVAL_SECONDS,t.getInt("NextPaymentSeconds"))):
                    (t.getBoolean("Funded")?INTERVAL_SECONDS-d.seconds:0));
                d.successfulPayments.put(id,Math.max(0,t.getInt("PaidCycles")));
                d.missedPayments.put(id,Math.max(0,t.getInt("MissedCycles")));
            }return d;}
        @Override public CompoundTag save(CompoundTag tag){tag.putInt("Seconds",seconds);ListTag list=new ListTag();for(var e:funded.entrySet()){
            CompoundTag t=new CompoundTag();t.putUUID("Id",e.getKey());t.putBoolean("Funded",e.getValue());
            t.putInt("NextPaymentSeconds",nextPaymentSeconds.getOrDefault(e.getKey(),INTERVAL_SECONDS-seconds));
            t.putInt("PaidCycles",successfulPayments.getOrDefault(e.getKey(),0));
            t.putInt("MissedCycles",missedPayments.getOrDefault(e.getKey(),0));list.add(t);
        }tag.put("Factions",list);return tag;}
    }
    /** Historic balances remain in their NBT fields until physical warehouses have room. */
    public static void migrate(MinecraftServer server){PhysicalWarehouses.importAll(server);}
    public static int balance(MinecraftServer server,UUID faction,String good){
        return PhysicalWarehouses.factionStock(server,faction,good);
    }
    /** System grants must have a physical destination; no invisible ledger credits. */
    public static void credit(MinecraftServer server,UUID faction,String good,int amount){
        SharedStockpile.get(server).add(faction,good,amount,PhysicalWarehouses.sharedCapacity(server,faction));
    }
    public static boolean debit(MinecraftServer server,UUID faction,String good,int amount){
        return amount>0&&PhysicalWarehouses.payHeadquarters(server,faction,Map.of(good,amount));
    }
    private static int ceil(int n,int div){return (n+div-1)/div;}
    public static int troops(MinecraftServer server,UUID faction){
        var m=MilitaryData.get(server);UUID owner=SharedStockpile.owner(server,faction);long total=m.reserve(owner)+m.assigned(owner);
        for(var t:KingdomData.get(server).towns.values())if(owner.equals(t.parentKingdom))total+=(long)m.reserve(t.id)+m.assigned(t.id);
        return (int)Math.min(1_000_000,total);
    }
    /** Members plus a small proxy for settlement size until civilian NPC population exists. */
    public static int population(MinecraftServer server,UUID faction){
        return DistrictData.get(server).city(SharedStockpile.owner(server,faction)).population;
    }
    public static int ironDue(MinecraftServer server,UUID id){return ceil(troops(server,id),100);}
    public static int wheatDue(MinecraftServer server,UUID id){return ceil(troops(server,id),25)+ceil(population(server,id),5);}
    public static boolean funded(MinecraftServer server,UUID id){id=SharedStockpile.owner(server,id);return Ledger.get(server).funded.getOrDefault(id,true);}
    /** Compatibility-only timer. Interfaces should use faction-specific remaining time. */
    public static int untilPayment(MinecraftServer server){return INTERVAL_SECONDS-Ledger.get(server).seconds;}
    public static int untilPayment(MinecraftServer server,UUID id){
        id=SharedStockpile.owner(server,id);Ledger ledger=Ledger.get(server);
        if(!ledger.funded.getOrDefault(id,true))return 0;
        return ledger.nextPaymentSeconds.getOrDefault(id,INTERVAL_SECONDS);
    }
    public static int goldDue(MinecraftServer server,UUID id){return DistrictData.get(server).payroll(server,id);}
    private static Map<String,Integer> upkeepGoods(MinecraftServer server,UUID id){
        return Map.of("IRON",ironDue(server,id),"WHEAT",wheatDue(server,id));
    }
    /** Gold ingots and nuggets are now the ONLY payroll source; no spendable Crown balance. */
    public static boolean canPayNext(MinecraftServer server,UUID id){
        return PhysicalWarehouses.canPayUpkeep(server,id,upkeepGoods(server,id),goldDue(server,id));
    }
    public static String upkeepStatus(MinecraftServer server,UUID id){
        if(funded(server,id))return "ACTIVE - next charge in "+untilPayment(server,id)+"s";
        var h=PhysicalWarehouses.headquarters(server,id);
        if(h==null)return "Missing settlement banner / dimension";

        int iron=ironDue(server,id),wheat=wheatDue(server,id),gold=goldDue(server,id);
        if(!canPayNext(server,id))return "Need "+iron+" iron, "+wheat+" wheat and "+gold+
            " gold nuggets worth (1 ingot = 9 nuggets) in the kingdom stockpile";
        return "Ready: restores automatically within one second";
    }
    public static int paidCycles(MinecraftServer server,UUID id){return Ledger.get(server).successfulPayments.getOrDefault(id,0);}
    public static int missedCycles(MinecraftServer server,UUID id){return Ledger.get(server).missedPayments.getOrDefault(id,0);}
    /** Called once per server second. Recover immediately when supplies/payroll are available.
     * A successful recovery starts a fresh 300-second clock: no double billing. */
    public static void tick(MinecraftServer server){
        Ledger ledger=Ledger.get(server);
        ledger.seconds=(ledger.seconds+1)%INTERVAL_SECONDS;
        if(ledger.seconds==0)migrate(server); // Legacy stock migration is still bounded to one pass per five minutes.
        KingdomData land=KingdomData.get(server);
        Set<UUID> ids=new HashSet<>(land.kingdoms.keySet());for(var t:land.towns.values())if(t.parentKingdom==null)ids.add(t.id);
        boolean changed=false;
        for(UUID id:ids){
            boolean wasFunded=ledger.funded.getOrDefault(id,true);
            int remaining=ledger.nextPaymentSeconds.getOrDefault(id,wasFunded?INTERVAL_SECONDS:0);
            if(wasFunded&&remaining>1){
                ledger.nextPaymentSeconds.put(id,remaining-1);ledger.funded.putIfAbsent(id,true);
                continue;
            }
            if(!canPayNext(server,id)){
                // Record the failed cycle only once; never punish citizens every second while unpaid.
                if(wasFunded){
                    DistrictData.get(server).cycle(server,id,false);
                    ledger.missedPayments.merge(id,1,Integer::sum);
                    changed=true;
                }
                ledger.funded.put(id,false);ledger.nextPaymentSeconds.put(id,0);
                if(wasFunded)informFaction(server,land,id,"Upkeep unpaid. "+upkeepStatus(server,id)+". Operations paused until supplied.");
                continue;
            }
            // Atomic physical material + gold payroll deduction, then deposit new gold taxes.
            boolean paid=PhysicalWarehouses.payUpkeep(server,id,upkeepGoods(server,id),goldDue(server,id));
            DistrictData.get(server).cycle(server,id,paid);
            if(paid){
                ledger.funded.put(id,true);
                ledger.nextPaymentSeconds.put(id,INTERVAL_SECONDS);
                ledger.successfulPayments.merge(id,1,Integer::sum);
                changed=true;
                if(!wasFunded)informFaction(server,land,id,"Upkeep restored! Settlement operations resumed immediately. Next payment in 5 minutes.");
            }else{
                if(wasFunded){ledger.missedPayments.merge(id,1,Integer::sum);changed=true;}
                ledger.funded.put(id,false);ledger.nextPaymentSeconds.put(id,0);
            }
        }
        if(ledger.funded.keySet().retainAll(ids))changed=true;
        if(ledger.nextPaymentSeconds.keySet().retainAll(ids))changed=true;
        if(ledger.successfulPayments.keySet().retainAll(ids))changed=true;
        if(ledger.missedPayments.keySet().retainAll(ids))changed=true;
        // Save the countdown periodically; status transitions and payments save immediately.
        if(changed||ledger.seconds%15==0)ledger.setDirty();
    }
    private static void informFaction(MinecraftServer server,KingdomData land,UUID id,String message){
        for(ServerPlayer p:server.getPlayerList().getPlayers()){
            var k=land.ofPlayer(p.getUUID());var t=land.townOf(p.getUUID());
            if(k!=null&&id.equals(SharedStockpile.owner(server,k.id))||t!=null&&id.equals(SharedStockpile.owner(server,t.id)))
                p.sendSystemMessage(net.minecraft.network.chat.Component.literal(message));
        }
    }
    public static String depositAvailable(ServerPlayer p,UUID faction,String good,int limit){
        if(limit<1||limit>2304)return "Invalid deposit limit.";
        int amount=PhysicalWarehouses.storeFromPlayer(p,faction,p.blockPosition(),good,limit);
        return amount>0?"Stored "+amount+" "+good+" in the kingdom stockpile.":"Need a kingdom stockpile with free capacity and carried resources.";
    }
    public static Item goodsItem(String good){return switch(good){
        case "IRON"->Items.IRON_INGOT;case "WHEAT"->Items.WHEAT;case "COAL"->Items.COAL;
        case "LOGS"->Items.OAK_LOG;case "STONE"->Items.COBBLESTONE;
        case "EMERALDS"->Items.EMERALD;case "GOLD"->Items.GOLD_INGOT;case "GOLD_NUGGETS"->Items.GOLD_NUGGET;default->null;
    };}
    /** Material transfers are physical and local; no more virtual treasury deposits. */
    public static String deposit(ServerPlayer p,UUID faction,String good,int amount){
        if(amount<1||amount>2304)return "Invalid deposit quantity.";
        if(carried(p,good)<amount)return "Need "+amount+" "+good+" in your inventory.";
        int moved=PhysicalWarehouses.storeFromPlayer(p,faction,p.blockPosition(),good,amount);
        return moved==amount?"Deposited "+moved+" "+good+" at the kingdom stockpile.":
            moved>0?"Stored "+moved+"; kingdom stockpile needs more capacity.":"No available kingdom stockpile; move within 96 blocks of a Warehouse Core.";
    }
    public static int carried(ServerPlayer p,String good){
        Item item=goodsItem(good);if(item==null)return 0;
        int count=0;for(ItemStack slot:p.getInventory().items)if(slot.is(item)&&!slot.hasTag())count+=slot.getCount();
        return count;
    }
    public static String withdraw(ServerPlayer p,UUID faction,String good,int limit){
        if(limit<1||limit>64)return "Invalid withdrawal amount.";
        int taken=PhysicalWarehouses.takeToPlayer(p,faction,p.blockPosition(),good,limit);
        return taken>0?"Withdrew "+taken+" "+good+" from the nearby warehouse.":"No nearby stocked warehouse or inventory space.";
    }
    public static String depositAllCarried(ServerPlayer p,UUID faction){
        int count=0;
        for(String good:OperationsData.GOODS)count+=PhysicalWarehouses.storeFromPlayer(p,faction,p.blockPosition(),good,2304);
        return count>0?"Stored "+count+" physical resources.":"No resources transferred; check kingdom stockpile space.";
    }
}
