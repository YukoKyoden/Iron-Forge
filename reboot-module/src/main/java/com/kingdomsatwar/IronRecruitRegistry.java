package com.kingdomsatwar;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class IronRecruitRegistry {
    private static final DeferredRegister<EntityType<?>> TYPES=
        DeferredRegister.create(ForgeRegistries.ENTITY_TYPES,KingdomsAtWar.MOD_ID);
    public static final RegistryObject<EntityType<IronRecruit>> SOLDIER=
        TYPES.register("iron_recruit",()->
            EntityType.Builder.of((EntityType<IronRecruit> type,net.minecraft.world.level.Level level)->
                    new IronRecruit(type,level),MobCategory.CREATURE)
                .sized(0.6F,1.95F).clientTrackingRange(8).build("iron_recruit"));
    private IronRecruitRegistry(){}
    public static void init(IEventBus bus){
        TYPES.register(bus);bus.addListener(IronRecruitRegistry::registerAttributes);
    }
    private static void registerAttributes(EntityAttributeCreationEvent event){
        event.put(SOLDIER.get(),IronRecruit.createRecruitAttributes().build());
    }
}
