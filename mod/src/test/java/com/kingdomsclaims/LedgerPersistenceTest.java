package com.kingdomsclaims;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
/** Runs actual SavedData serialization without a running Minecraft world. */
public final class LedgerPersistenceTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        UUID kingdom=UUID.randomUUID(),town=UUID.randomUUID();SharedStockpile stock=new SharedStockpile();
        check(stock.add(kingdom,"IRON",80,1000)==80,"iron deposited");check(stock.add(kingdom,"WHEAT",40,1000)==40,"wheat deposited");
        check(!stock.pay(kingdom,Map.of("IRON",81,"WHEAT",1)),"short bundle rejected");check(stock.amount(kingdom,"WHEAT")==40,"short bundle atomic");
        check(!stock.pay(kingdom,Map.of("IRON",-1)),"negative debit rejected");check(stock.add(kingdom,"BAD",10,1000)==0,"unknown resource rejected");
        stock.add(town,"IRON",20,1000);check(stock.mergeTown(town,kingdom),"town pooled");check(stock.amount(kingdom,"IRON")==100&&stock.amount(town,"IRON")==0,"town resources transferred once");check(stock.mergeTown(town,kingdom)&&stock.amount(kingdom,"IRON")==100,"repeated merge cannot duplicate");
        check(stock.add(kingdom,"GOLD",1,1000)==1,"gold deposited");check(stock.payWithGoldChange(kingdom,Map.of("GOLD",1),8),"gold paid with change");
        stock.add(kingdom,"minecraft:glass",12,1000);
        SharedStockpile restored=SharedStockpile.load(stock.save(new CompoundTag()));
        check(restored.amount(kingdom,"IRON")==100&&restored.amount(kingdom,"WHEAT")==40&&restored.amount(kingdom,"GOLD")==0&&restored.amount(kingdom,"GOLD_NUGGETS")==8,"all balances survive NBT roundtrip");
        check(restored.amount(kingdom,"minecraft:glass")==12,"specialty building items survive NBT roundtrip");
        check(restored.pay(kingdom,Map.of("IRON",100,"WHEAT",40)),"restored balances spendable");check(!restored.pay(kingdom,Map.of("IRON",1)),"cannot spend twice");
        RegisteredBuildings registry=new RegisteredBuildings();var b=RegisteredBuildings.selection(kingdom,"minecraft:overworld",new BlockPos(10,60,10),new BlockPos(20,70,20),"house");b.capacity=4;b.dirty=false;b.next=12000;registry.buildings.add(b);
        RegisteredBuildings reloaded=RegisteredBuildings.load(registry.save(new CompoundTag()));var actual=reloaded.buildings.get(0);
        check(actual.owner.equals(kingdom)&&actual.min.equals(b.min)&&actual.max.equals(b.max)&&actual.capacity==4&&!actual.dirty&&actual.next==12000,"registration bounds, capacity and production clock survive NBT roundtrip");
        EconomyService.Ledger clock=new EconomyService.Ledger();clock.funded.put(kingdom,true);clock.nextPaymentSeconds.put(kingdom,123);clock.successfulPayments.put(kingdom,7);
        var savedClock=EconomyService.Ledger.load(clock.save(new CompoundTag()));check(savedClock.nextPaymentSeconds.get(kingdom)==123&&savedClock.successfulPayments.get(kingdom)==7,"upkeep timer and payment count survive NBT roundtrip");
        System.out.println("Actual stockpile, registered-building and upkeep NBT persistence regressions passed.");
    }
}
