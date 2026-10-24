from pathlib import Path
import shutil

root=Path("src")
source=Path("reboot-module/src")
for p in source.rglob("*"):
    if p.is_file():
        dst=root/p.relative_to(source)
        dst.parent.mkdir(parents=True,exist_ok=True)
        shutil.copy2(p,dst)

entry=Path("src/main/java/com/kingdomsatwar/KingdomsAtWar.java")
s=entry.read_text()
needle="WarTable.init(FMLJavaModLoadingContext.get().getModEventBus());"
assert needle in s
s=s.replace(needle,needle+"MineBlocks.init(FMLJavaModLoadingContext.get().getModEventBus());MinecraftForge.EVENT_BUS.register(MineRuntime.instance());",1)
entry.write_text(s)

state=Path("src/main/java/com/kingdomsatwar/WarState.java")
s=state.read_text()
needle="private boolean dirty;"
assert needle in s
s=s.replace(needle,needle+" public void markDirty(){ dirty=true; }",1)
# Iron from a mine must come from supported, finite physical mines, not passive old chunk bonuses.
import re
s,n=re.subn(r'k\\.iron=Math\\.min\\(10000,k\\.iron\\+\\(int\\)\\(Math\\.max\\(1,owned\\)\\*6\\)\\+mines\\*24\\);',
            'k.iron=Math.min(10000,k.iron); // geological mine controllers create iron',s)
if not n:
    # Alternative spacing or v0.6 formatting; abort loudly instead of silently duplicating economies.
    raise RuntimeError("Could not find old passive iron generation")
state.write_text(s)

gradle=Path("build.gradle")
s=gradle.read_text()
s,n=re.subn(r"version = '[^']+'","version = '0.7.0-geology-alpha'",s,count=1)
assert n
gradle.write_text(s)
toml=Path("src/main/resources/META-INF/mods.toml")
s=toml.read_text()
s,n=re.subn(r'(?m)^version="[^"]+"','version="0.7.0-geology-alpha"',s,count=1)
assert n
toml.write_text(s)
print("Reboot mining module integrated, passive iron disabled, version updated.")
