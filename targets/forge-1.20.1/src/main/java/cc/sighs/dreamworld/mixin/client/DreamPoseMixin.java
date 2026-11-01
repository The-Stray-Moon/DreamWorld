package cc.sighs.dreamworld.mixin.client;

import cc.sighs.dreamworld.client.DreamClientState;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Draws other players as lying down while they dream.
 *
 * <p>The sleeping body is a purely visual fiction: a dreamer keeps walking around in a normal pose,
 * and neither the server nor the dreamer's own client treats them as asleep. Only the clients of the
 * other players replace the pose, which is exactly what this hook does - and only for a
 * {@link RemotePlayer}, so the local player is never affected even if the list ever names them.</p>
 *
 * <p>Nothing else has to change: {@code PlayerRenderer} picks the sleeping rotation from the pose,
 * and the hitbox stays the standing one, so collision keeps matching what the server simulates.</p>
 */
@Mixin(Entity.class)
public abstract class DreamPoseMixin {
    @Inject(method = "getPose", at = @At("HEAD"), cancellable = true)
    private void dreamworld$remoteSleepingPose(CallbackInfoReturnable<Pose> cir) {
        if ((Object) this instanceof RemotePlayer remote && DreamClientState.isDreamer(remote.getUUID())) {
            cir.setReturnValue(Pose.SLEEPING);
        }
    }
}
