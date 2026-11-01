package cc.sighs.dreamworld.mixin;

import cc.sighs.dreamworld.DWObjects;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Grinding a lone dream crystal back into dream powder; there is no grindstone recipe type. */
@Mixin(GrindstoneMenu.class)
public abstract class GrindstoneMenuMixin {
    @Inject(method = "computeResult", at = @At("HEAD"), cancellable = true)
    private void dreamworld$grindCrystal(ItemStack top, ItemStack bottom,
            CallbackInfoReturnable<ItemStack> cir) {
        if (top.is(DWObjects.DREAM_CRYSTAL.get()) && bottom.isEmpty()) {
            cir.setReturnValue(new ItemStack(DWObjects.DREAM_POWDER.get()));
        }
    }
}
