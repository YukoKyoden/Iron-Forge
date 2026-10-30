import json
from pathlib import Path
root=Path(__file__).resolve().parents[1]
for path in (root/'src/main/resources').rglob('*.json'):
    json.loads(path.read_text())
version='0.4.26'
assert version in (root/'build.gradle').read_text()
assert version in (root/'src/main/resources/META-INF/mods.toml').read_text()
assert (root/'src/main/resources/assets/kingdomsclaims/models/item/architect_wand.json').exists()
print('Resource JSON, wand assets and version validation passed.')
