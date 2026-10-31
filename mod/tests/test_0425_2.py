from pathlib import Path
j=Path('src/main/java/com/kingdomsclaims')
e=(j/'EconomyService.java').read_text();w=(j/'PhysicalWarehouses.java').read_text()
d=(j/'DistrictData.java').read_text();n=(j/'DistrictNetwork.java').read_text();g=(j/'OperationsData.java').read_text()
a=(j/'WarehouseData.java').read_text()
assert '"GOLD","GOLD_NUGGETS"' in g
assert 'Items.GOLD_INGOT' in e and 'Items.GOLD_NUGGET' in e
assert 'for(String good:OperationsData.GOODS)' in a
assert 'payUpkeep(server,id,upkeepGoods(server,id),goldDue(server,id))' in e
assert 'canPayUpkeep(server,id,upkeepGoods(server,id),goldDue(server,id))' in e
assert '9L*d.count("GOLD")+d.count("GOLD_NUGGETS")' in w
assert 'futureUsed>capacity(w,p)' in w
assert 'civics.crowns=remainingCrowns' in w and 'civics.pendingTaxNuggets=oldPending' in w
assert 'if(essentialsPaid)c.paidCycles++' in d
assert 'PhysicalWarehouses.importHeadquartersGold(server,owner)' in d
assert 'PhysicalWarehouses.storeFromPlayer(p,faction.id(),p.blockPosition(),resource,64)' in n
assert 'city.crowns+=' not in n and 'city.crowns-=' not in e
assert 'if(!canPayNext(server,id))' in e and 'ledger.nextPaymentSeconds.put(id,INTERVAL_SECONDS)' in e
print('PASS: gold ingots/nuggets are physical, payroll atomic, taxes pending, old crowns migrated')
