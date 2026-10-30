from pathlib import Path
import json
p=Path('src/main/java/com/kingdomsclaims');r=Path('src/main/resources/assets/kingdomsclaims')
s=(p/'StorageExpansionBlock.java').read_text();m=(p/'KingdomsClaims.java').read_text()
assert 'extends HorizontalDirectionalBlock' in s and 'context.getHorizontalDirection().getOpposite()' in s
assert 'b.add(FACING,FILL)' in s and 'state.setValue(FACING' in s
assert 'strength(2.5f,3600000f).noOcclusion()' not in m
variants=json.loads((r/'blockstates/storage_expansion.json').read_text())['variants']
assert len(variants)==28
for direction in ('north','east','south','west'):
 for n in range(7):
  assert f'facing={direction},fill={n}' in variants
  model=json.loads((r/'models/block'/f'storage_expansion_{n}.json').read_text())
  assert model['parent']=='minecraft:block/cube' and len(model['textures'])>=6
  img=(r/'textures/block'/f'warehouse_shelf_front_{n}.png').read_bytes()
  assert img.startswith(b'\x89PNG\r\n\x1a\n')
for name in ('warehouse_shelf_side','warehouse_shelf_top'):
 assert (r/'textures/block'/f'{name}.png').exists()
print('PASS: directional opaque bookshelf shelves, 7 cheap static fill models, 28 state variants')
