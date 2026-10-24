package com.kingdomsatwar;

import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.RegistryObject;

/**
 * Stable accessors for mine blocks. All mine blocks and their items now use
 * WarTable's proven shared DeferredRegister, avoiding separate registration
 * timing and RegistryObject#get() during the item registration callback.
 */
public final class MineBlocks {
    public static final RegistryObject<Block> CONTROLLER = WarTable.MINE_CONTROLLER;
    public static final RegistryObject<Block> SUPPORT = WarTable.MINE_SUPPORT;
    private MineBlocks() {}
}
