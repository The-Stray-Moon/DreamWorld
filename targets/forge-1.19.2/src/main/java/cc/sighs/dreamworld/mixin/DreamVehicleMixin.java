package cc.sighs.dreamworld.mixin;

import cc.sighs.dreamworld.boat.DreamBoatGate;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes a dream boat indestructible.
 *
 * <p>A dreamer glides over every block at speed, usually through terrain no boat was meant to touch,
 * and there is nothing in the dream to replace a destroyed boat with. Damage is therefore refused
 * outright while a dreaming player drives the boat: no hurt flash, no build-up to the destroy
 * threshold, no dropped boat item.</p>
 *
 * <p>The damage funnel lives in {@code VehicleEntity}, not in {@code Boat}, so the hook sits there
 * and narrows itself to boats.</p>
 */
@Mixin(Boat.class)
public abstract class DreamVehicleMixin {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void dreamworld$dreamBoatInvulnerable(DamageSource source, float amount,
            CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Boat boat && DreamBoatGate.isDreamBoat(boat)) {
            cir.setReturnValue(false);
        }
    }
}
