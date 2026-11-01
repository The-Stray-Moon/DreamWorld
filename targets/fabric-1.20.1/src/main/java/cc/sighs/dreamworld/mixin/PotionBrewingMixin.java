package cc.sighs.dreamworld.mixin;

import cc.sighs.dreamworld.DWObjects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Dream tea brewing. Vanilla has no data-driven brewing recipe, and Fabric exposes no builder, so
 * the powder and the bottle are recognised at the query points the brewing stand uses.
 *
 * <p>1.20.1 has no {@code isInput} query: the potion slot already accepts glass bottles, so only
 * the ingredient check, the mix check and the mix itself need the tea recipe.</p>
 */
@Mixin(PotionBrewing.class)
public abstract class PotionBrewingMixin {
    @Inject(method = "isIngredient", at = @At("HEAD"), cancellable = true)
    private static void dreamworld$isIngredient(ItemStack stack,
            CallbackInfoReturnable<Boolean> cir) {
        if (stack.is(DWObjects.DREAM_POWDER.get())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "hasMix", at = @At("HEAD"), cancellable = true)
    private static void dreamworld$hasMix(ItemStack input, ItemStack ingredient,
            CallbackInfoReturnable<Boolean> cir) {
        if (input.is(net.minecraft.world.item.Items.GLASS_BOTTLE)
                && ingredient.is(DWObjects.DREAM_POWDER.get())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mix", at = @At("HEAD"), cancellable = true)
    private static void dreamworld$mix(ItemStack ingredient, ItemStack input,
            CallbackInfoReturnable<ItemStack> cir) {
        if (input.is(net.minecraft.world.item.Items.GLASS_BOTTLE)
                && ingredient.is(DWObjects.DREAM_POWDER.get())) {
            cir.setReturnValue(new ItemStack(DWObjects.DREAM_TEA.get()));
        }
    }
}
