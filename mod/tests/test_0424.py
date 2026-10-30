"""v0.4.24 crash regression and vanilla-scale resource shelf asset checks."""
from pathlib import Path
import struct
import json
src=Path('src/main/java/com/kingdomsclaims/KingdomOverviewClient.java').read_text()
assert 'Long focus=selectedClaimChunk;' in src
assert 'if(hoverBlock!=null){' in src
assert 'focus=Long.valueOf(ChunkPos.asLong(' in src
assert 'Long focus=hoverBlock!=null?ChunkPos.asLong(' not in src
assets=Path('src/main/resources/assets/kingdomsclaims')
assert len(json.loads((assets/'blockstates/storage_expansion.json').read_text())['variants'])==28
for n in range(7):
  png=(assets/'textures/block'/f'warehouse_shelf_front_{n}.png').read_bytes()
  assert png.startswith(b'\x89PNG\r\n\x1a\n')
  assert struct.unpack('>II',png[16:24])==(16,16)
for name in ('warehouse_shelf_side','warehouse_shelf_top'):
  png=(assets/'textures/block'/f'{name}.png').read_bytes()
  assert struct.unpack('>II',png[16:24])==(16,16)
for n in range(7):
  model=json.loads((assets/'models/block'/f'storage_expansion_{n}.json').read_text())
  assert model['parent']=='minecraft:block/cube'
  assert model['textures']['north'].endswith(f'warehouse_shelf_front_{n}')
print('PASS v0.4.24: null-safe 3D claim render and 16x16 vanilla-inspired shelf art')
