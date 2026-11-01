package cc.sighs.dreamworld.mixin.client;

import cc.sighs.dreamworld.client.DreamBlockTint;
import cc.sighs.dreamworld.client.DreamClientState;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.minecraft.util.ARGB;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Pins the time-driven environment attributes while dreaming.
 *
 * <p>26.1 derives sky angles, star brightness, the sunrise colour and the lightmap's
 * {@code SKY_LIGHT_FACTOR} from an {@link EnvironmentAttributeProbe}. Overriding only
 * {@code SkyRenderState} is not enough: the lightmap reads the probe separately and a server
 * night still darkens the world. Forcing the attributes at the probe keeps every consumer -
 * sky, fog and lightmap - on the same permanent noon.</p>
 */
@Mixin(EnvironmentAttributeProbe.class)
public abstract class DreamAttributeMixin {
    @Inject(method = "getValue", at = @At("HEAD"), cancellable = true)
    private <T> void dreamworld$forceDreamAttribute(EnvironmentAttribute<T> attribute,
            float partialTick, CallbackInfoReturnable<T> cir) {
        if (!DreamClientState.isDreaming()) {
            return;
        }
        Object forced = forcedValue(attribute);
        if (forced != null) {
            @SuppressWarnings("unchecked")
            T value = (T) forced;
            cir.setReturnValue(value);
        }
    }

    private static Object forcedValue(EnvironmentAttribute<?> attribute) {
        if (attribute == EnvironmentAttributes.SKY_LIGHT_FACTOR) {
            return 1.0F;
        }
        if (attribute == EnvironmentAttributes.SUN_ANGLE) {
            return 0.0F;
        }
        if (attribute == EnvironmentAttributes.MOON_ANGLE) {
            return 180.0F;
        }
        if (attribute == EnvironmentAttributes.STAR_ANGLE) {
            return 0.0F;
        }
        if (attribute == EnvironmentAttributes.STAR_BRIGHTNESS) {
            return 0.0F;
        }
        if (attribute == EnvironmentAttributes.SUNRISE_SUNSET_COLOR) {
            return 0;
        }
        if (attribute == EnvironmentAttributes.SKY_LIGHT_COLOR) {
            return 0xFFFFFFFF;
        }
        if (attribute == EnvironmentAttributes.AMBIENT_LIGHT_COLOR) {
            return 0xFFFFFFFF;
        }
        if (attribute == EnvironmentAttributes.FOG_COLOR) {
            Vec3 sky = DreamBlockTint.skyColor();
            return ARGB.colorFromFloat(1.0F, (float) sky.x, (float) sky.y, (float) sky.z);
        }
        if (attribute == EnvironmentAttributes.SKY_COLOR) {
            Vec3 sky = DreamBlockTint.skyColor();
            return ARGB.colorFromFloat(1.0F, (float) sky.x, (float) sky.y, (float) sky.z);
        }
        if (attribute == EnvironmentAttributes.CLOUD_COLOR) {
            return 0xFFFFFFFF;
        }
        return null;
    }
}
