package cc.sighs.dreamworld.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lays the collision box of a synthetic sleeper down with the body.
 *
 * <p>An AABB cannot be rotated, so a lying body is expressed as the axis-aligned box that encloses
 * it: the standing height becomes the length along the facing direction, the standing width becomes
 * the thickness above the ground, and the two are mixed by the yaw to get the footprint. A body
 * facing exactly along an axis gets a clean box; anything in between gets the box that encloses the
 * lying shape, which is how the hitbox follows the model instead of standing upright through it.</p>
 *
 * <p>Real sleepers are left alone: an entity in a bed has a sleeping position and already sits in a
 * box the bed gave it. Only the synthetic sleepers of this mod take this path - the dream creature
 * copies on the server, and the other players' clients drawing a dreamer as asleep.</p>
 */
@Mixin(Entity.class)
public abstract class DreamHitboxMixin {
    @Inject(method = "makeBoundingBox()Lnet/minecraft/world/phys/AABB;", at = @At("HEAD"), cancellable = true)
    private void dreamworld$lyingHitbox(CallbackInfoReturnable<AABB> cir) {
        Entity self = (Entity) (Object) this;
        if (self.getPose() != Pose.SLEEPING) {
            return;
        }
        // A bed sleeper keeps the box the bed already accounts for.
        if (self instanceof LivingEntity living && living.isSleeping()) {
            return;
        }
        EntityDimensions standing = self.getDimensions(Pose.STANDING);
        double length = standing.height();
        double width = standing.width();
        double yaw = Math.toRadians(self.getYRot());
        double sin = Math.abs(Math.sin(yaw));
        double cos = Math.abs(Math.cos(yaw));
        double halfX = (length * sin + width * cos) * 0.5D;
        double halfZ = (length * cos + width * sin) * 0.5D;
        double x = self.getX();
        double y = self.getY();
        double z = self.getZ();
        cir.setReturnValue(new AABB(x - halfX, y, z - halfZ, x + halfX, y + width, z + halfZ));
    }
}
