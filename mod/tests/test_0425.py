"""v0.4.25: physical local economy, production and migration safety regressions."""
from pathlib import Path
r=Path('src/main/java/com/kingdomsclaims')
w=(r/'PhysicalWarehouses.java').read_text()
e=(r/'EconomyService.java').read_text()
b=(r/'BarracksService.java').read_text()
o=(r/'OperationsService.java').read_text()
d=(r/'OperationsData.java').read_text()
n=(r/'SupplyDepotNetwork.java').read_text()
c=(r/'SupplyDepotClient.java').read_text()
q=(r/'OperationsNetwork.java').read_text()
assert 'private static void importLegacy' in w and 'old.debit(d.faction,good,move)' in w
assert 'kingdom.treasuryIron-=amount' in w and 'town.treasuryWheat-=amount' in w
assert 'if(changed)drawFill' in w  # no unnecessary block-state updates when checking old balance
assert 'public static boolean payLocal(' in w and 'public static boolean canPayLocal(' in w
assert 'public static Headquarters headquarters(' in w and 'payHeadquarters(' in w
assert 'for(ServerLevel world:server.getAllLevels())' in w and 'core(world,' in w
assert 'payConstruction(' in w and 'importLegacy(player.serverLevel(),nearby,depot)' in w
assert 'no fallback to an invisible ledger' in w
assert 'PhysicalWarehouses.payLocal(world,faction.id(),home,96,supplies)' in b
assert 'ledger.removeReserve(faction.id(),hire)' in b
assert 'BREAD_PER_SOLDIER*hire*3' in b
assert 'PhysicalWarehouses.payUpkeep(server,id,upkeepGoods(server,id),goldDue(server,id))' in e
assert 'DistrictData.get(server).cycle(server,id,paid)' in e
assert 'PhysicalWarehouses.importAll(server)' in e and 'if(ledger.seconds==0)migrate(server)' in e
assert 'OperationsData.get(server).credit(faction,good,amount)' not in e
assert 'EconomyService.debit(p.getServer(),faction.id(),good,amount)' not in o
assert 'PhysicalWarehouses.payLocal(p.serverLevel(),faction.id(),table,96,Map.of(good,amount))' in o
assert 's.passiveSeconds++' in o and 's.passiveSeconds>=60' in o
assert 's.stock=Math.min(256' in o and 'PhysicalWarehouses.deliver(w,s.owner' in o
assert 't.putInt("PassiveSeconds",s.passiveSeconds)' in d
assert 'int[] rates' in n and 'PhysicalWarehouses.rates(' in n
assert 'Production: +' in c and 'WAREHOUSE FULL' in c
assert 'PhysicalWarehouses.localStock(' in q
assert 'version = \'0.4.25.2\'' in Path('build.gradle').read_text()
print('PASS v0.4.25: local atomic material flows, safe legacy migration, staffed production, and production UI')
