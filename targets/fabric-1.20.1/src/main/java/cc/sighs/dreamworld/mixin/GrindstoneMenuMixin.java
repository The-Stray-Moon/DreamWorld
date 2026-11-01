package cc.sighs.dreamworld.mixin;

import cc.sighs.dreamworld.DWObjects;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Grinding a lone dream crystal back into dream powder; there is no grindstone recipe type.
 *
 * <p>1.20.1 computes the grindstone result in {@code createResult()} from the two input slots,
 * instead of the later {@code computeResult(top, bottom)} helper. Vanilla ends that method with a
 * menu broadcast, so the replacement result has to be broadcast explicitly as well.</p>
 */
@Mixin(GrindstoneMenu.class)
public abstract class GrindstoneMenuMixin {
    @Shadow
    @Final
    private Container repairSlots;
    @Shadow
    @Final
    private Container resultSlots;

    @Inject(method = "createResult", at = @At("HEAD"), cancellable = true)
    private void dreamworld$grindCrystal(CallbackInfo ci) {
        ItemStack top = this.repairSlots.getItem(0);
        ItemStack bottom = this.repairSlots.getItem(1);
        if (top.is(DWObjects.DREAM_CRYSTAL.get()) && bottom.isEmpty()) {
            this.resultSlots.setItem(0, new ItemStack(DWObjects.DREAM_POWDER.get()));
            ((AbstractContainerMenu) (Object) this).broadcastChanges();
            ci.cancel();
        }
    }
}
