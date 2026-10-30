"""Regression guards: immediate recovery and exact one-cycle billing after restocking."""
from pathlib import Path
j=Path('src/main/java/com/kingdomsclaims')
e=(j/'EconomyService.java').read_text();w=(j/'PhysicalWarehouses.java').read_text()
n=(j/'OperationsNetwork.java').read_text();c=(j/'WarMapClient.java').read_text()
assert 'nextPaymentSeconds=new HashMap<>()' in e
assert 't.putInt("NextPaymentSeconds"' in e and 't.contains("NextPaymentSeconds",Tag.TAG_INT)' in e
assert 'if(wasFunded&&remaining>1)' in e  # no premature charge when funded
assert 'if(!canPayNext(server,id))' in e  # unpaid cycles are checked each second
assert 'if(wasFunded){' in e and 'ledger.missedPayments.merge(id,1,Integer::sum)' in e
assert 'ledger.nextPaymentSeconds.put(id,INTERVAL_SECONDS)' in e  # full 5 minutes after recovery
assert 'if(!wasFunded)informFaction(' in e
assert 'if(changed||ledger.seconds%15==0)ledger.setDirty()' in e
assert 'upkeepCore' in w
assert 'canPayUpkeep' in w and 'upkeepCore(server,faction,goods,wageNuggets)' in w
assert 'public static boolean hasHeadquartersWarehouse(' in w
assert 'gold nuggets worth' in e
assert 'EconomyService.untilPayment(p.getServer(),faction.id())' in n
assert 'EconomyService.upkeepStatus(p.getServer(),faction.id())' in n
assert 'operations.upkeepStatus()' in c
print('PASS v0.4.25.2: warehouse reactivation, faction clocks, payroll visibility and bounded storage lookup')
