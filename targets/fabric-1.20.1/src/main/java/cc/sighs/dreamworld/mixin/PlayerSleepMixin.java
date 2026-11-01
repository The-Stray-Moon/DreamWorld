package cc.sighs.dreamworld.mixin;

import cc.sighs.dreamworld.dream.DreamSleepEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps a ground sleeper asleep without a bed and forwards wake-ups to the shared handler.
 *
 * <p>Vanilla drops the sleep state as soon as the sleeping position holds no bed; the dream lets a
 * dream walker sleep on bare ground, so that one automatic wake-up is skipped. Every other wake-up
 * still runs and is reported to the shared code.</p>
 */
@Mixin(Player.class)
public abstract class PlayerSleepMixin {
    @Redirect(method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;stopSleepInBed(ZZ)V"))
    private void dreamworld$keepGroundSleep(Player player, boolean wakeImmediately,
            boolean updateSleepingPlayers) {
        BlockPos pos = player.getSleepingPos().orElse(null);
        if (!DreamSleepEvents.isInternalWake() && pos != null
                && !player.level().getBlockState(pos).is(net.minecraft.tags.BlockTags.BEDS)) {
            return;
        }
        player.stopSleepInBed(wakeImmediately, updateSleepingPlayers);
    }

    @Inject(method = "stopSleepInBed", at = @At("HEAD"))
    private void dreamworld$reportWakeUp(boolean wakeImmediately, boolean updateSleepingPlayers,
            CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player && !DreamSleepEvents.isInternalWake()) {
            DreamSleepEvents.onWakeUp(player);
        }
    }
}
