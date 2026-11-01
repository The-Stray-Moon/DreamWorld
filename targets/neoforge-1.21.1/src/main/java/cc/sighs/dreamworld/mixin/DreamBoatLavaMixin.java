package cc.sighs.dreamworld.mixin;

import cc.sighs.dreamworld.boat.DreamBoatGate;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Lets a dream boat treat lava as a drivable fluid, like water. */
@Mixin(Boat.class)
public abstract class DreamBoatLavaMixin {
    @Unique
    public boolean canBoatInFluid(FluidState state) {
        Boat boat = (Boat) (Object) this;
        if (DreamBoatGate.isDreamBoat(boat) && state.is(FluidTags.LAVA)) {
            return true;
        }
        return state.supportsBoating(boat);
    }
}
