package cc.sighs.dreamworld.mixin.client;

import cc.sighs.dreamworld.client.DreamClientState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes the sky of the dream world permanently daytime and bluer. There is no event for sky colour,
 * so the client level has to be patched directly.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    /** Deeper blue than any vanilla biome, matching the palette of the dream world. */
    private static final Vec3 dreamworld$DREAM_SKY_COLOR = new Vec3(0.23D, 0.45D, 0.95D);

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
            cir.setReturnValue(dreamworld$DREAM_SKY_COLOR);
        }
    }

    @Inject(method = "getStarBrightness(F)F", at = @At("HEAD"), cancellable = true)
    private void dreamworld$hideStars(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (DreamClientState.isDreaming()) {
            // The sun still follows the real time of day, so keep the night sky free of stars.
            cir.setReturnValue(0.0F);
        }
    }
}
