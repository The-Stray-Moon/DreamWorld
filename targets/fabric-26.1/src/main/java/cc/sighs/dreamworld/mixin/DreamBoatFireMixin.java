package cc.sighs.dreamworld.mixin;

import cc.sighs.dreamworld.boat.DreamBoatGate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps a dream boat from catching fire while it is rowed through lava. */
@Mixin(Entity.class)
public abstract class DreamBoatFireMixin {
    @Inject(method = "fireImmune", at = @At("HEAD"), cancellable = true)
    private void dreamworld$dreamBoatFireImmune(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof AbstractBoat boat && DreamBoatGate.isDreamBoat(boat)) {
            cir.setReturnValue(true);
        }
    }
}
