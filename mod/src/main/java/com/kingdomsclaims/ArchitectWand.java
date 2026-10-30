package com.kingdomsclaims;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
public final class ArchitectWand extends Item {
    public ArchitectWand(Properties p){super(p);}
    private void activate(Level world,Player player){if(player==null||player.getCooldowns().isOnCooldown(this))return;player.getCooldowns().addCooldown(this,10);if(world.isClientSide)DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->WorldSelectionClient.registerBuilding());}
    @Override public InteractionResultHolder<ItemStack> use(Level w,Player p,InteractionHand h){activate(w,p);return InteractionResultHolder.sidedSuccess(p.getItemInHand(h),w.isClientSide);}
    @Override public InteractionResult useOn(UseOnContext c){activate(c.getLevel(),c.getPlayer());return InteractionResult.sidedSuccess(c.getLevel().isClientSide);}
}
