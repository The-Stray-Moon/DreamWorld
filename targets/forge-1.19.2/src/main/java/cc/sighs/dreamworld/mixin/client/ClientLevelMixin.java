package cc.sighs.dreamworld.mixin.client;

import cc.sighs.dreamworld.client.DreamBlockTint;
import cc.sighs.dreamworld.client.DreamClientState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes the sky of the dream world permanently daytime, blue and independent of the server clock.
 * There is no event for sky colour or for the sun angle, so the client level has to be patched
 * directly. Every hook only fires for the dreaming local player, the real world keeps its clock.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Inject(method = "getSkyDarken(F)F", at = @At("HEAD"), cancellable = true)
    private void dreamworld$forceDaylight(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (DreamClientState.isDreaming()) {
            // 1.0 is the brightest value; the lightmap uses it as the daylight factor.
            cir.setReturnValue(1.0F);
        }
    }

    @Inject(method = "getSkyColor(Lnet/minecraft/world/phys/Vec3;F)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private void dreamworld$dreamSkyColor(Vec3 position, float partialTick, CallbackInfoReturnable<Vec3> cir) {
        if (DreamClientState.isDreaming()) {
            // Built from the palette rather than from the constant, so swapping the palettes recolours
            // the sky along with the water instead of leaving a stale blue overhead.
            cir.setReturnValue(DreamBlockTint.skyColor());
        }
    }

    @Inject(method = "getStarBrightness(F)F", at = @At("HEAD"), cancellable = true)
    private void dreamworld$hideStars(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (DreamClientState.isDreaming()) {
            cir.setReturnValue(0.0F);
        }
    }

}
