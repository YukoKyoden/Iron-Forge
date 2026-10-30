from pathlib import Path
import json
p=Path('src/main/java/com/kingdomsclaims');r=Path('src/main/resources/assets/kingdomsclaims')
w=(p/'PhysicalWarehouses.java').read_text();d=(p/'WarehouseData.java').read_text();main=(p/'KingdomsClaims.java').read_text();ui=(p/'KingdomOverviewClient.java').read_text()
for part in ('MAX_LINK_DISTANCE=10','MAX_CORE_RADIUS=50','MIN_CORE_SPACING=128','reachesCore','coreTooNear','candidateCore','onPlace','onBreak','within(origin,n,CORE_RADIUS_SQ)','!reachesCore(origin,after)'):
    assert part in w,part
assert 'MAX_MODULES=100' in d and 'ShelvesMigrated' in d and 'd.shelves.add' in w
assert 'TickEvent' not in w and 'getHeight(Heightmap.Types.WORLD_SURFACE' in ui
assert 'edgeLine(event,line,eye' in ui
assert 'CREATIVE_TABS.register(bus)' in main and 'KINGDOMS_TAB' in main
assert 'Storage expansions:' not in (p/'SupplyDepotClient.java').read_text()
for name in ('warehouse_core_front','warehouse_core_side','warehouse_core_top','warehouse_core_bottom',*[f'warehouse_shelf_{i}' for i in range(4)]):
    assert (r/'textures/block'/f'{name}.png').read_bytes().startswith(b'\x89PNG\r\n\x1a\n')
for n in range(4): assert json.loads((r/'models/block'/f'storage_expansion_{n}.json').read_text())['parent']=='minecraft:block/cube'
assert 'minecraft:block/cube' in (r/'models/block/supply_depot.json').read_text()
print('PASS: 10-block chains, 50-block radius, 128-block core spacing, 100 shelves, static textures, creative tab, outline cleanup')
