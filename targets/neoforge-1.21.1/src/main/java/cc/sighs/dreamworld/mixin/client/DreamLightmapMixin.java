package cc.sighs.dreamworld.mixin.client;

import cc.sighs.dreamworld.client.DreamClientState;
import cc.sighs.dreamworld.spi.DreamPlatform;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lifts the dream's unlit places out of pitch black.
 *
 * <p>The lightmap texture is a 16x16 grid: the x axis is block light, the y axis is sky light, and
 * {@code getBrightness} converts an axis coordinate into a brightness factor. The first of the two
 * calls in {@code updateLightTexture} builds the sky-light axis, which is the one that decides how
 * dark an enclosed space is - a cave has no sky light and no light sources, so its cell of the map is
 * black.</p>
 *
 * <p>While dreaming, that axis is floored at {@code ambientSkyLight} from the client config, which
 * acts like a permanent, uniform ambient light: caves and the inside of hills become readable while
 * anything already lit by the sky keeps its colour, because its sky light is above the floor anyway.
 * Block light is untouched, so torches and lava still read as bright spots.</p>
 */
@Mixin(LightTexture.class)
public abstract class DreamLightmapMixin {
    @Redirect(
            method = "updateLightTexture",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LightTexture;getBrightness"
                            + "(Lnet/minecraft/world/level/dimension/DimensionType;I)F",
                    ordinal = 0))
    private float dreamworld$ambientSkyLight(DimensionType type, int skyLight) {
        if (DreamClientState.isDreaming()) {
            return LightTexture.getBrightness(type,
                    Math.max(skyLight, DreamPlatform.get().config().ambientSkyLight()));
        }
        return LightTexture.getBrightness(type, skyLight);
    }
}
