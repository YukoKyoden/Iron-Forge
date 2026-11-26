package com.hearthhunger.mixin;
import com.hearthhunger.HearthHunger;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
/** Only suppress the natural-regeneration rule read inside FoodData.tick.
 * Starvation, exhaustion, saturation and all external healing are untouched. */
@Mixin(FoodData.class)
public abstract class FoodDataMixin {
    @ModifyExpressionValue(method="tick(Lnet/minecraft/world/entity/player/Player;)V",
        at=@At(value="INVOKE",target="Lnet/minecraft/world/level/GameRules;getBoolean(Lnet/minecraft/world/level/GameRules$Key;)Z"),
        require=1)
    private boolean hearthhunger$replaceNaturalHealing(boolean original,Player player){
        return original&&!HearthHunger.usesCustomRegen(player);
    }
}
