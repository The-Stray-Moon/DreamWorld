package cc.sighs.dreamworld.mixin;

import cc.sighs.dreamworld.boat.DreamBoatGate;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Lets a dream boat treat lava as a drivable fluid, like water. */
@Mixin(AbstractBoat.class)
public abstract class DreamBoatLavaMixin {
    @Redirect(method = {"checkInWater", "isUnderwater", "getWaterLevelAbove"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/material/FluidState;is"
                            + "(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean dreamworld$lavaIsWater(FluidState state, TagKey<Fluid> tag) {
        if (tag == FluidTags.WATER && DreamBoatGate.isDreamBoat((AbstractBoat) (Object) this)
                && state.is(FluidTags.LAVA)) {
            return true;
        }
        return state.is(tag);
    }
}
