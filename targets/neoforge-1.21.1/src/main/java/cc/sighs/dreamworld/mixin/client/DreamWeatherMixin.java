package cc.sighs.dreamworld.mixin.client;

import cc.sighs.dreamworld.client.DreamClientState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forces clear weather on the dream layer.
 *
 * <p>Weather is sent to the client as a level-wide value, so a storm in reality would otherwise keep
 * raining inside the dream. {@code getRainLevel} and {@code getThunderLevel} are the single source
 * every weather consumer reads - the rain and snow quads in {@code LevelRenderer}, the rain splash
 * particles, the thunder flash and the sky darkening - and both live on {@link Level}, not on
 * {@link ClientLevel}, so this has to hook the base class. The override only applies while the local
 * player is dreaming and never touches the real layer.</p>
 */
@Mixin(Level.class)
public abstract class DreamWeatherMixin {
    @Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
    private void dreamworld$noRain(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (dreamworld$dreamingOnThisLevel()) {
            cir.setReturnValue(0.0F);
        }
    }

    /**
     * Thunder is multiplied by the rain level in vanilla, but the flash and the sky flash timer read
     * the value directly, so it is pinned as well.
     */
    @Inject(method = "getThunderLevel", at = @At("HEAD"), cancellable = true)
    private void dreamworld$noThunder(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (dreamworld$dreamingOnThisLevel()) {
            cir.setReturnValue(0.0F);
        }
    }

    /** True only for the client level the dreaming local player is standing in. */
    private boolean dreamworld$dreamingOnThisLevel() {
        return DreamClientState.isDreaming() && ((Object) this) instanceof ClientLevel level
                && level == net.minecraft.client.Minecraft.getInstance().level;
    }
}
