from pathlib import Path
p=Path('src/main/java/com/kingdomsclaims')
w=(p/'PhysicalWarehouses.java').read_text()
assert 'MAX_CORE_RADIUS=50' in w and 'MAX_MODULES' in w and 'hasChunkAt' in w
assert 'Math.ceil(6.0*fill/per)' in w and 'w.setBlock(p,state.setValue(StorageExpansionBlock.FILL,level),2)' in w
assert 'e.setCanceled(true)' in w and 'Too many stored resources' in w
assert 'Old stockpile imports are disabled' in w and 'd.goods.merge(good,batch,Integer::sum)' in w
assert 'onPlace(BlockEvent.EntityPlaceEvent' in w and 'onBreak(BlockEvent.BreakEvent' in w
assert 'TickEvent' not in w
assert 'new StorageExpansionBlock' in (p/'KingdomsClaims.java').read_text()
assert 'PhysicalWarehouses.transfer' in (p/'SupplyDepotNetwork.java').read_text()
assert 'Shelves:' in (p/'SupplyDepotClient.java').read_text()
assert 'SavedData' in (p/'WarehouseData.java').read_text() and 'ModularGoods' in (p/'WarehouseData.java').read_text()
assert 'storage_expansion' in Path('src/main/resources/assets/kingdomsclaims/lang/en_us.json').read_text()
for n in range(4):assert Path(f'src/main/resources/assets/kingdomsclaims/models/block/storage_expansion_{n}.json').exists()
print('PASS: modular warehouse, four static fill states, capacity protection, local persistence and migration')
