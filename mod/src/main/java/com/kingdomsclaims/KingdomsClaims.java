package com.kingdomsclaims;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(KingdomsClaims.MODID)
public final class KingdomsClaims {
    public static final String MODID = "kingdomsclaims";
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
    public static final RegistryObject<EntityType<SoldierEntity>> SOLDIER=ENTITIES.register("militia",()->
            EntityType.Builder.of(SoldierEntity::new,MobCategory.CREATURE).sized(0.6f,1.95f).clientTrackingRange(8).build("kingdomsclaims:militia"));
    public static final RegistryObject<Block> MINE_MARKER=BLOCKS.register("mine_marker",()->new ResourceMarkerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3f),"mine"));
    public static final RegistryObject<Block> LUMBER_MARKER=BLOCKS.register("lumber_marker",()->new ResourceMarkerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(3f),"lumber camp"));
    public static final RegistryObject<Item> LUMBER_MARKER_ITEM=ITEMS.register("lumber_marker",()->new BlockItem(LUMBER_MARKER.get(),new Item.Properties()));
    public static final RegistryObject<Item> MINE_MARKER_ITEM=ITEMS.register("mine_marker",()->new BlockItem(MINE_MARKER.get(),new Item.Properties()));
    public static final RegistryObject<Item> ARCHITECT_WAND=ITEMS.register("architect_wand",()->new ArchitectWand(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Block> KINGDOM_MANAGER = BLOCKS.register("kingdom_manager",()->
            new KingdomManagerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(3.5f)));
    public static final RegistryObject<Item> KINGDOM_MANAGER_ITEM = ITEMS.register("kingdom_manager",()->new BlockItem(KINGDOM_MANAGER.get(),new Item.Properties()));
    public static final RegistryObject<Block> SUPPLY_DEPOT = BLOCKS.register("supply_depot",()->
            new SupplyDepotBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(3.0f,3600000f)));
    public static final RegistryObject<Item> SUPPLY_DEPOT_ITEM = ITEMS.register("supply_depot",()->new BlockItem(SUPPLY_DEPOT.get(),new Item.Properties()));
    public static final RegistryObject<Block> STORAGE_EXPANSION=BLOCKS.register("storage_expansion",()->
            new StorageExpansionBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f,3600000f)));
    public static final RegistryObject<Item> STORAGE_EXPANSION_ITEM=ITEMS.register("storage_expansion",()->new BlockItem(STORAGE_EXPANSION.get(),new Item.Properties()));
    public static final RegistryObject<Block> WAR_TABLE = BLOCKS.register("war_table",()->
            new WarTableBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(3.0f)));
    public static final RegistryObject<Item> WAR_TABLE_ITEM = ITEMS.register("war_table",()->new BlockItem(WAR_TABLE.get(),new Item.Properties()));
    public static final RegistryObject<Block> BARRACKS = BLOCKS.register("barracks_controller",()->
            new BarracksBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(4f).requiresCorrectToolForDrops()));
    public static final RegistryObject<Item> BARRACKS_ITEM = ITEMS.register("barracks_controller",()->new BlockItem(BARRACKS.get(),new Item.Properties()));

    public static final RegistryObject<Block> KINGDOM_BANNER = BLOCKS.register("kingdom_banner",
            () -> new TerritoryBannerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(3.5F, 3600000F).noOcclusion()));
    public static final RegistryObject<Block> TOWN_BANNER = BLOCKS.register("town_banner",
            () -> new TerritoryBannerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(3.5F, 3600000F).noOcclusion()));
    // No items for upper segments: they are placed/removed atomically with the base banner.
    public static final RegistryObject<Block> KINGDOM_BANNER_EXTENSION = BLOCKS.register("kingdom_banner_extension",
            () -> new BannerExtensionBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(-1.0F, 3600000F).noOcclusion().noCollission().noLootTable()));
    public static final RegistryObject<Block> TOWN_BANNER_EXTENSION = BLOCKS.register("town_banner_extension",
            () -> new BannerExtensionBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(-1.0F, 3600000F).noOcclusion().noCollission().noLootTable()));
    public static final RegistryObject<Item> KINGDOM_BANNER_ITEM = ITEMS.register("kingdom_banner",
            () -> new BlockItem(KINGDOM_BANNER.get(), new Item.Properties()));
    public static final RegistryObject<Item> TOWN_BANNER_ITEM = ITEMS.register("town_banner",
            () -> new BlockItem(TOWN_BANNER.get(), new Item.Properties()));

    public static final RegistryObject<CreativeModeTab> KINGDOMS_TAB = CREATIVE_TABS.register("kingdoms",()->
            CreativeModeTab.builder().title(Component.translatable("itemGroup.kingdomsclaims.main"))
                .icon(()->KINGDOM_BANNER_ITEM.get().getDefaultInstance())
                .displayItems((parameters,output)->{
                    output.accept(KINGDOM_BANNER_ITEM.get());output.accept(TOWN_BANNER_ITEM.get());
                    output.accept(KINGDOM_MANAGER_ITEM.get());output.accept(WAR_TABLE_ITEM.get());
                    output.accept(BARRACKS_ITEM.get());output.accept(SUPPLY_DEPOT_ITEM.get());
                    output.accept(STORAGE_EXPANSION_ITEM.get());output.accept(ARCHITECT_WAND.get());output.accept(MINE_MARKER_ITEM.get());output.accept(LUMBER_MARKER_ITEM.get());
                }).build());
    public KingdomsClaims() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ENTITIES.register(bus);
        bus.addListener(this::attributes);
        ITEMS.register(bus);
        CREATIVE_TABS.register(bus);
        TerritoryNetwork.register();
        TownHallNetwork.register();
        BarracksNetwork.register();
        WarTableNetwork.register();
        WarMapNetwork.register();
        OperationsNetwork.register();
        DistrictNetwork.register();
        BlueprintNetwork.register();
        SupplyDepotNetwork.register();BuildingNetwork.register();ManagerNetwork.register();ProductionNetwork.register();
        MinecraftForge.EVENT_BUS.register(ClaimEvents.class);MinecraftForge.EVENT_BUS.register(RegisteredBuildings.class);MinecraftForge.EVENT_BUS.register(ResourceMarkers.class);
        MinecraftForge.EVENT_BUS.register(PhysicalWarehouses.class);
        MinecraftForge.EVENT_BUS.register(PlayerBuildEvents.class);
        MinecraftForge.EVENT_BUS.register(KingdomCommands.class);
        MinecraftForge.EVENT_BUS.register(HousingSurvey.class);
        MinecraftForge.EVENT_BUS.register(BlueprintCommands.class);
    }
    private void attributes(EntityAttributeCreationEvent event){event.put(SOLDIER.get(),SoldierEntity.createAttributes().build());}
}
