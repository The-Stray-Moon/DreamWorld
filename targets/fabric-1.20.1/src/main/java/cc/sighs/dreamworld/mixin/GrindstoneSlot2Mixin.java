package cc.sighs.dreamworld.mixin;

import cc.sighs.dreamworld.DWObjects;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Widens one grindstone input slot; vanilla only accepts damageable or enchantable items. */
@Mixin(targets = "net.minecraft.world.inventory.GrindstoneMenu$3")
public abstract class GrindstoneSlot2Mixin {
    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void dreamworld$allowCrystal(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.is(DWObjects.DREAM_CRYSTAL.get())) {
            cir.setReturnValue(true);
        }
    }
}
