package cc.sighs.dreamworld.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla wakes a sleeper as soon as the sleeping position holds no bed. The dream lets a dream
 * walker sleep on bare ground, so that single check is answered "yes" for a bed-less player.
 *
 * <p>{@code PlayerSleepMixin} only covers the wake-up call inside {@code Player#tick}; this one
 * covers {@code LivingEntity#tick}, which checks the bed independently and calls
 * {@code stopSleeping()} directly.</p>
 */
@Mixin(LivingEntity.class)
public abstract class DreamBedCheckMixin {
    @Inject(method = "checkBedExists", at = @At("HEAD"), cancellable = true)
    private void dreamworld$keepGroundSleep(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player player) {
            BlockPos pos = player.getSleepingPos().orElse(null);
            if (pos != null && !player.level().getBlockState(pos).is(BlockTags.BEDS)) {
                cir.setReturnValue(true);
            }
        }
    }
}
