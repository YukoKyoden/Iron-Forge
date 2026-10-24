package com.kingdomsatwar;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Two lightweight physical blocks; neither has a ticking block entity. */
public final class MineBlocks {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,KingdomsAtWar.MOD_ID);
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,KingdomsAtWar.MOD_ID);
    public static final RegistryObject<Block> CONTROLLER=BLOCKS.register("mine_controller",()->
        new Block(BlockBehaviour.Properties.copy(Blocks.STONE_BRICKS).strength(3.5f)) {
            @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
                if(!level.isClientSide && player instanceof ServerPlayer p) MineRuntime.instance().interact(p,pos);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        });
    public static final RegistryObject<Block> SUPPORT=BLOCKS.register("mine_support",()->
        new Block(BlockBehaviour.Properties.copy(Blocks.OAK_LOG).strength(2.5f)));
    public static final RegistryObject<Item> CONTROLLER_ITEM=ITEMS.register("mine_controller",()->
        new BlockItem(CONTROLLER.get(),new Item.Properties()));
    public static final RegistryObject<Item> SUPPORT_ITEM=ITEMS.register("mine_support",()->
        new BlockItem(SUPPORT.get(),new Item.Properties()));
    private MineBlocks(){}
    public static void init(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);}
}
