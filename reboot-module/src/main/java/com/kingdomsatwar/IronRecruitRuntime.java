package com.kingdomsatwar;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Hire a real soldier from an adult villager, with a vanilla emerald cost. */
public final class IronRecruitRuntime {
    public static final int HIRE_COST=16;
    private static final IronRecruitRuntime INSTANCE=new IronRecruitRuntime();
    private IronRecruitRuntime(){}
    public static IronRecruitRuntime instance(){return INSTANCE;}
    @SubscribeEvent public void onVillagerInteract(PlayerInteractEvent.EntityInteract event){
        if(event.getHand()!=InteractionHand.MAIN_HAND)return;
        if(!(event.getTarget() instanceof Villager villager))return;
        ItemStack hand=event.getEntity().getMainHandItem();
        if(!hand.is(Items.EMERALD))return; // normal trading remains untouched
        if(event.getLevel().isClientSide){
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);return;
        }
        if(!(event.getEntity() instanceof ServerPlayer player)
            ||!(event.getLevel() instanceof ServerLevel level))return;
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
        WarState state=KingdomsAtWar.instance().get(player.server);
        WarState.Kingdom kingdom=state.kingdom(player.getUUID());
        if(kingdom==null){tell(player,"Found a kingdom at the War Table before hiring soldiers.");return;}
        if(villager.isBaby()){tell(player,"Only adult villagers can become recruits.");return;}
        if(!player.getAbilities().instabuild && hand.getCount()<HIRE_COST){
            tell(player,"Hiring costs 16 emeralds. Hold a full stack of at least 16.");return;
        }
        int nearby=level.getEntitiesOfClass(IronRecruit.class,new AABB(player.blockPosition()).inflate(96),
            recruit->kingdom().equals(kingdom.id)).size();
        if(nearby>=16){tell(player,"Maximum 16 active recruits for your kingdom in this area.");return;}
        IronRecruit recruit=IronRecruitRegistry.SOLDIER.get().create(level);
        if(recruit==null){tell(player,"Failed to create soldier.");return;}
        recruit.moveTo(villager.getX(),villager.getY(),villager.getZ(),villager.getYRot(),0.0F);
        recruit.assign(player,kingdom.id);
        if(!level.addFreshEntity(recruit)){tell(player,"Could not spawn soldier; no emeralds were taken.");return;}
        if(!player.getAbilities().instabuild)hand.shrink(HIRE_COST);
        villager.discard();
        tell(player,"Villager recruited! Right-click empty-handed: Follow/Hold. Sneak-right-click: Guard. Right-click with a sword to equip.");
    }
    private static void tell(ServerPlayer player,String msg){
        player.sendSystemMessage(Component.literal("[Iron Forge] "+msg));
    }
}
