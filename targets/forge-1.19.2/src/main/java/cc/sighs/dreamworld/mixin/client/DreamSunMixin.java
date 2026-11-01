package cc.sighs.dreamworld.mixin.client;

import cc.sighs.dreamworld.client.DreamClientState;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * Pins the dream sky: the sun draw is skipped and the clock reads noon.
 *
 * <p>1.19.2 builds the sun as a {@link BufferBuilder} and uploads it with
 * {@link BufferUploader#drawWithShader}. The draw is skipped while dreaming, and the rendered
 * buffer is released so its vertex memory is not leaked. {@code getTimeOfDay} is a
 * {@code LevelTimeAccess} default method, so it cannot be overwritten on {@code ClientLevel};
 * redirecting the two calls in {@code renderSky} pins the sun angle and the sunrise glow instead.
 * The rest of the clock-driven colours are already pinned by {@code ClientLevelMixin}.</p>
 */
@Mixin(LevelRenderer.class)
public abstract class DreamSunMixin {
    private static final String DRAW_WITH_SHADER =
            "Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;)V";
    private static final String BIND_TEXTURE =
            "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(ILnet/minecraft/resources/ResourceLocation;)V";
    private static final String GET_TIME_OF_DAY =
            "Lnet/minecraft/client/multiplayer/ClientLevel;getTimeOfDay(F)F";

    @Redirect(method = "renderSky",
            at = @At(value = "INVOKE", target = DRAW_WITH_SHADER, ordinal = 0),
            slice = @Slice(from = @At(value = "INVOKE", target = BIND_TEXTURE)))
    private static void dreamworld$hideSun(BufferBuilder.RenderedBuffer mesh) {
        if (DreamClientState.isDreaming()) {
            mesh.release();
            return;
        }
        BufferUploader.drawWithShader(mesh);
    }

    /** Sunrise glow: the call at offset 246 in {@code renderSky}. */
    @Redirect(method = "renderSky",
            at = @At(value = "INVOKE", target = GET_TIME_OF_DAY, ordinal = 0))
    private float dreamworld$sunriseNoon(ClientLevel level, float partialTick) {
        return DreamClientState.isDreaming() ? 0.0F : level.getTimeOfDay(partialTick);
    }

    /** Sun and moon angle: the call at offset 592 in {@code renderSky}. */
    @Redirect(method = "renderSky",
            at = @At(value = "INVOKE", target = GET_TIME_OF_DAY, ordinal = 1))
    private float dreamworld$celestialNoon(ClientLevel level, float partialTick) {
        return DreamClientState.isDreaming() ? 0.0F : level.getTimeOfDay(partialTick);
    }
}
