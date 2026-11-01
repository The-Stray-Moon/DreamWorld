package cc.sighs.dreamworld.mixin.client;

import cc.sighs.dreamworld.client.DreamEntityVisibility;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hides ordinary creatures from a dreaming client; the server filters dream creatures itself.
 *
 * <p>26.1 renders from extracted render states, so there is no per-entity render method left to
 * cancel. The dispatch-time culling test still sees the entity and runs before its state is
 * submitted, which is the closest equivalent.</p>
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void dreamworld$hideLiving(E entity, Frustum frustum,
            double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof LivingEntity living && DreamEntityVisibility.shouldHide(living)) {
            cir.setReturnValue(false);
        }
    }
}
