package cc.sighs.dreamworld.mixin;

import cc.sighs.dreamworld.DWObjects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Dream tea brewing. Vanilla has no data-driven brewing recipe, and Fabric exposes no builder, so
 * the bottle and the powder are recognised at the query points the brewing stand and its
 * container checks use. 26.1 split the old {@code isInput} into container and potion ingredient
 * queries.
 */
@Mixin(PotionBrewing.class)
public abstract class PotionBrewingMixin {
    @Inject(method = "isIngredient", at = @At("HEAD"), cancellable = true)
    private void dreamworld$isIngredient(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.is(DWObjects.DREAM_POWDER.get())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isContainerIngredient", at = @At("HEAD"), cancellable = true)
    private void dreamworld$isContainerIngredient(ItemStack stack,
            CallbackInfoReturnable<Boolean> cir) {
        if (stack.is(Items.GLASS_BOTTLE)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "hasMix", at = @At("HEAD"), cancellable = true)
    private void dreamworld$hasMix(ItemStack input, ItemStack ingredient,
            CallbackInfoReturnable<Boolean> cir) {
        if (input.is(Items.GLASS_BOTTLE) && ingredient.is(DWObjects.DREAM_POWDER.get())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mix", at = @At("HEAD"), cancellable = true)
    private void dreamworld$mix(ItemStack ingredient, ItemStack input,
            CallbackInfoReturnable<ItemStack> cir) {
        if (input.is(Items.GLASS_BOTTLE) && ingredient.is(DWObjects.DREAM_POWDER.get())) {
            cir.setReturnValue(new ItemStack(DWObjects.DREAM_TEA.get()));
        }
    }
}
