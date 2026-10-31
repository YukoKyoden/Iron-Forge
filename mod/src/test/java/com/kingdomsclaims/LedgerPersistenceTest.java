package com.kingdomsclaims;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
/** Runs actual SavedData serialization without a running Minecraft world. */
public final class LedgerPersistenceTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        UUID kingdom=UUID.randomUUID(),town=UUID.randomUUID();SharedStockpile stock=new SharedStockpile();
        check(stock.add(kingdom,"IRON",80,1000)==80,"iron deposited");check(stock.add(kingdom,"WHEAT",40,1000)==40,"wheat deposited");
        check(!stock.pay(kingdom,Map.of("IRON",81,"WHEAT",1)),"short bundle rejected");check(stock.amount(kingdom,"WHEAT")==40,"short bundle atomic");
        check(!stock.pay(kingdom,Map.of("IRON",-1)),"negative debit rejected");check(stock.add(kingdom,"BAD",10,1000)==0,"unknown resource rejected");
        stock.add(town,"IRON",20,1000);check(stock.mergeTown(town,kingdom),"town pooled");check(stock.amount(kingdom,"IRON")==100&&stock.amount(town,"IRON")==0,"town resources transferred once");check(stock.mergeTown(town,kingdom)&&stock.amount(kingdom,"IRON")==100,"repeated merge cannot duplicate");
        check(stock.add(kingdom,"GOLD",1,1000)==1,"gold deposited");check(stock.payWithGoldChange(kingdom,Map.of("GOLD",1),8),"gold paid with change");
        CompoundTag saved=stock.save(new CompoundTag());
        for(var entry:saved.getList("Factions",net.minecraft.nbt.Tag.TAG_COMPOUND)){var row=(CompoundTag)entry;if(row.getUUID("Id").equals(kingdom))row.getCompound("Goods").putInt("minecraft:glass",12);}
        SharedStockpile restored=SharedStockpile.load(saved);
        check(restored.amount(kingdom,"IRON")==100&&restored.amount(kingdom,"WHEAT")==40&&restored.amount(kingdom,"GOLD")==0&&restored.amount(kingdom,"GOLD_NUGGETS")==8,"all balances survive NBT roundtrip");
        check(restored.amount(kingdom,"minecraft:glass")==12,"specialty building items survive NBT roundtrip");
        check(restored.pay(kingdom,Map.of("IRON",100,"WHEAT",40)),"restored balances spendable");check(!restored.pay(kingdom,Map.of("IRON",1)),"cannot spend twice");
        RegisteredBuildings registry=new RegisteredBuildings();var b=RegisteredBuildings.selection(kingdom,"minecraft:overworld",new BlockPos(10,60,10),new BlockPos(20,70,20),"house");b.capacity=4;b.dirty=false;b.next=12000;registry.buildings.add(b);
        RegisteredBuildings reloaded=RegisteredBuildings.load(registry.save(new CompoundTag()));var actual=reloaded.buildings.get(0);
        check(actual.owner.equals(kingdom)&&actual.min.equals(b.min)&&actual.max.equals(b.max)&&actual.capacity==4&&!actual.dirty&&actual.next==12000,"registration bounds, capacity and production clock survive NBT roundtrip");
        b.type="farm";CompoundTag legacy=registry.save(new CompoundTag());legacy.remove("ProductionIntervalTicks");var converted=RegisteredBuildings.load(legacy);check(converted.buildings.get(0).next==18000,"one-minute production deadline upgraded to six minutes");var convertedAgain=RegisteredBuildings.load(converted.save(new CompoundTag()));check(convertedAgain.buildings.get(0).next==18000,"upgrade applied exactly once across reloads");
        OperationsData operations=new OperationsData();var site=new OperationsData.Site();site.passiveSeconds=300;operations.sites.put(site.id,site);check(OperationsData.load(operations.save(new CompoundTag())).sites.get(site.id).passiveSeconds==300,"six-minute outpost progress survives reload");
        b.type="mine";b.railMine=true;b.marker=new BlockPos(12,80,12);b.halfIron=1;var mine=RegisteredBuildings.load(registry.save(new CompoundTag())).buildings.get(0);check(mine.railMine&&mine.marker.equals(b.marker)&&mine.halfIron==1,"mine marker and fractional iron persist");
        b.autoProducer=true;b.entrance=new BlockPos(12,64,12);b.connectedRails=40;b.detectedLogs=80;b.detectedLeaves=120;b.surveyDetail="40 rails; 10 iron / 6 min";var marker=RegisteredBuildings.load(registry.save(new CompoundTag())).buildings.get(0);check(marker.autoProducer&&marker.entrance.equals(b.entrance)&&marker.connectedRails==40&&marker.detectedLogs==80&&marker.detectedLeaves==120&&marker.surveyDetail.equals(b.surveyDetail)&&marker.halfIron==1&&marker.next==b.next,"automatic marker position, scan readout, fraction and next production survive reload");
        MilitaryData military=new MilitaryData();var squad=military.create(kingdom,"kingdom","Test squad","minecraft:overworld",new BlockPos(0,64,0));military.addReserve(kingdom,90);check(military.assign(squad,90)&&military.reserve(kingdom)==0,"assign does not duplicate reserve");squad.strength=60;squad.fielded=30;squad.order="follow";squad.commander=UUID.randomUUID();squad.rallyX=100;squad.marching=true;military.advance();check(squad.locationX==0,"physical squad never also advances numerically");var squads=MilitaryData.load(military.save(new CompoundTag()));var restoredSquad=squads.armies.get(squad.id);check(squads.assigned(kingdom)==90&&restoredSquad.order.equals("follow")&&restoredSquad.commander.equals(squad.commander),"squad manpower, order and commander survive reload");restoredSquad.strength+=restoredSquad.fielded;restoredSquad.fielded=0;squads.advance();check(restoredSquad.locationX==1.5&&squads.assigned(kingdom)==90,"distant squad advances without troop duplication");
        EconomyService.Ledger clock=new EconomyService.Ledger();clock.funded.put(kingdom,true);clock.nextPaymentSeconds.put(kingdom,123);clock.successfulPayments.put(kingdom,7);
        var savedClock=EconomyService.Ledger.load(clock.save(new CompoundTag()));check(savedClock.nextPaymentSeconds.get(kingdom)==123&&savedClock.successfulPayments.get(kingdom)==7,"upkeep timer and payment count survive NBT roundtrip");
        ProductionData production=new ProductionData();ProductionWindow window=new ProductionWindow();window.add(100,2,7);production.windows.put(kingdom,window);var history=ProductionData.load(production.save(new CompoundTag()));check(history.windows.get(kingdom).total(159,2)==7&&history.windows.get(kingdom).total(160,2)==0,"production history persists and expires exactly");
        System.out.println("Actual stockpile, registered-building and upkeep NBT persistence regressions passed.");
    }
}
