package cc.sighs.dreamworld.mixin.client;

import cc.sighs.dreamworld.client.DreamClientState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
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
    /** 0.0 is noon: the sun sits at its highest point and the sunrise glow is not drawn. */
    private static final float dreamworld$DREAM_TIME_OF_DAY = 0.0F;

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
            cir.setReturnValue(DreamClientState.SKY_COLOR);
        }
    }

    @Inject(method = "getStarBrightness(F)F", at = @At("HEAD"), cancellable = true)
    private void dreamworld$hideStars(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (DreamClientState.isDreaming()) {
            cir.setReturnValue(0.0F);
        }
    }

    /**
     * {@code getTimeOfDay} is a default method of {@code LevelTimeAccess} and is therefore not part
     * of the {@link ClientLevel} class, so it cannot be injected into. Adding it as a normal method
     * overrides the default and pins everything clock driven: sun and moon angle, sunrise glow,
     * cloud colour and the sky gradient.
     */
    public float getTimeOfDay(float partialTick) {
        if (DreamClientState.isDreaming()) {
            return dreamworld$DREAM_TIME_OF_DAY;
        }
        Level level = (Level) (Object) this;
        // The LevelTimeAccess default, which cannot be reached through super from a mixin.
        return level.dimensionType().timeOfDay(level.dayTime());
    }
}
