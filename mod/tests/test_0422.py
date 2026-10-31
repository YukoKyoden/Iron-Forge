from pathlib import Path
import json
p=Path('src/main/java/com/kingdomsclaims');r=Path('src/main/resources/assets/kingdomsclaims')
s=(p/'KingdomOverviewClient.java').read_text();main=(p/'KingdomsClaims.java').read_text()
assert 'private boolean widgetsDirty=false;' in s
assert 'private void refreshWidgets(){widgetsDirty=true;}' in s
assert 'applyDeferredWidgets();' in s[s.index('public void render(GuiGraphics'):]
assert 'hand-building a RenderType.lines vertex format' in s
assert 'LevelRenderer.renderLineBox(event.getPoseStack(),lines,' in s
assert 'strength(2.5f,3600000f).noOcclusion()' not in main
assert '0.4.25' in Path('build.gradle').read_text()
for n in range(4):
 m=json.loads((r/'models/block'/f'storage_expansion_{n}.json').read_text())
 assert m['parent']=='minecraft:block/cube' and not m.get('elements')
for tex in ('wood','end','back','crate','crate_side','crate_top','sack','sack_side'):
 assert (r/'textures/block'/f'warehouse_shelf_{tex}.png').read_bytes().startswith(b'\x89PNG')
print('PASS: deferred claims GUI, safe perimeter drawing, opaque shelf geometry')
