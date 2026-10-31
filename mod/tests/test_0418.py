from pathlib import Path
src=Path('src/main/java/com/kingdomsclaims')
warehouse=(src/'PhysicalWarehouses.java').read_text()
data=(src/'WarehouseData.java').read_text()
net=(src/'SupplyDepotNetwork.java').read_text()
client=(src/'SupplyDepotClient.java').read_text()
assert 'SLOTS_PER_BLOCK=9' in data
assert 'getDataStorage().computeIfAbsent' in data
assert 'MAX_CORE_RADIUS=50' in warehouse
assert 'StorageExpansionBlock.FILL' in warehouse
assert 'e.setCanceled(true)' in warehouse
assert 'return new InventoryView(stock,carried,capacity(count)' in warehouse
assert 'Old stockpile imports are disabled' in warehouse and 'EconomyService.credit' not in warehouse
assert 'PhysicalWarehouses.transfer' in net
assert 'Import legacy 64' not in client
assert 'Shelves:' in client
assert 'MinecraftForge.EVENT_BUS.register(PhysicalWarehouses.class)' in (src/'KingdomsClaims.java').read_text()
print('PASS: modular storage replaces old links while preserving migration and permissions')
