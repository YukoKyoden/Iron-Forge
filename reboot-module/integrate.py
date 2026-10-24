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
s=s.replace(needle,needle+"MineControllerBlockEntity.init(FMLJavaModLoadingContext.get().getModEventBus());IronRecruitRegistry.init(FMLJavaModLoadingContext.get().getModEventBus());MinecraftForge.EVENT_BUS.register(MineRuntime.instance());MinecraftForge.EVENT_BUS.register(IronRecruitRuntime.instance());",1)
entry.write_text(s)

# Share the existing WarTable DeferredRegisters with the mine blocks/items.
# In the first preview the standalone mine DeferredRegisters caused an early
# RegistryObject#get() on the client before mine_support was present.
war_table = Path("src/main/java/com/kingdomsatwar/WarTable.java")
text = war_table.read_text()
anchor = "    private WarTable() {}"
assert anchor in text, "Could not find WarTable registration anchor"
registration = """
    // Mine entries share the WarTable DeferredRegisters. Instantiate blocks
    // only within block registry callbacks, never during static initialization.
    public static final RegistryObject<Block> MINE_CONTROLLER =
        BLOCKS.register("mine_controller", MineControllerBlock::new);
    public static final RegistryObject<Block> MINE_SUPPORT =
        BLOCKS.register("mine_support", () ->
            new Block(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS).strength(2.5f)));
    public static final RegistryObject<Item> MINE_CONTROLLER_ITEM =
        ITEMS.register("mine_controller", () -> new BlockItem(MINE_CONTROLLER.get(), new Item.Properties()));
    public static final RegistryObject<Item> MINE_SUPPORT_ITEM =
        ITEMS.register("mine_support", () -> new BlockItem(MINE_SUPPORT.get(), new Item.Properties()));
"""
assert "MINE_SUPPORT_ITEM" not in text, "Mine already registered"
text = text.replace(anchor,registration+"\n"+anchor,1)
war_table.write_text(text)

state=Path("src/main/java/com/kingdomsatwar/WarState.java")
s=state.read_text()
needle="private boolean dirty;"
assert needle in s
s=s.replace(needle,needle+" public void markDirty(){ dirty=true; }",1)
# Iron from a mine must come from supported, finite physical mines, not passive old chunk bonuses.
import re
old='k.iron=Math.min(10000,k.iron+(int)(Math.max(1,owned)*6)+mines*24);'
new='k.iron=Math.min(10000,k.iron); // mined iron comes only from registered tunnel mines'
n=s.count(old)
s=s.replace(old,new)
if not n:
    # Alternative spacing or v0.6 formatting; abort loudly instead of silently duplicating economies.
    raise RuntimeError("Could not find old passive iron generation")
# Reject obsolete menu-only mines so they cannot charge resources without production.
old='if(c.industry!=0)return "This chunk already has an economic building.";'
assert old in s
s=s.replace(old,old+chr(10)+'        if(type==2)return "Use the Mine Controller construction chest to build an iron mine.";')

state.write_text(s)

gradle=Path("build.gradle")
s=gradle.read_text()
s,n=re.subn(r"version = '[^']+'","version = '0.9.0-recruits-alpha'",s,count=1)
assert n
gradle.write_text(s)
toml=Path("src/main/resources/META-INF/mods.toml")
s=toml.read_text()
s,n=re.subn(r'(?m)^version="[^"]+"','version="0.9.0-recruits-alpha"',s,count=1)
assert n
toml.write_text(s)
print("Reboot mining module integrated, passive iron disabled, version updated.")
