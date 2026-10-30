from pathlib import Path
p=Path('src/main/java/com/kingdomsclaims')
w=(p/'PhysicalWarehouses.java').read_text()
b=(p/'BlueprintService.java').read_text()
o=(p/'OperationsService.java').read_text()
n=(p/'DistrictNetwork.java').read_text()
u=(p/'KingdomOverviewClient.java').read_text()
assert 'public static String payConstruction(' in w
assert 'public static int deliver(' in w and 'nearest(w,faction,destination,192)' in w
assert 'core(w,pos)' in w and 'capacity(w,nearby)-depot.total()' in w
assert 'PhysicalWarehouses.payConstruction(' in b
assert 'if(paymentIssue!=null)return no(paymentIssue)' in b
assert 'PhysicalWarehouses.deliver(' in o and 'if(c.amount>0)continue' in o
assert 'EconomyService.credit(server,c.destinationFaction,c.good,c.amount)' not in o
assert 'List<Marker> markers' in n and 'markers.add(new Marker("WAREHOUSE"' in n
assert 'for(DistrictNetwork.Marker marker:data.markers())' in u
assert 'Math.ceil(6.0*fill/per)' in w
print('PASS: server-validated local warehouse construction, physical mission cargo, and bounded map pins')
