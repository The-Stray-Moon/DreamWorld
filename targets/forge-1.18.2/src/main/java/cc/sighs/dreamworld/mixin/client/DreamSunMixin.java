package cc.sighs.dreamworld.mixin.client;

import cc.sighs.dreamworld.client.DreamClientState;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * Hides the sun on the dream layer.
 *
 * <p>The sky quad is drawn by {@code LevelRenderer#renderSky} right after the sun texture is bound,
 * before the moon. The dream has no day cycle, so the sun would sit motionless at its noon position
 * forever - the one thing in the sky that makes it look like a snapshot of the real world. Its draw
 * is skipped while dreaming; the moon is left alone, it stays below the horizon at the pinned noon
 * time anyway.</p>
 *
 * <p>The slice anchors on the {@code SUN_LOCATION} field use, which is exactly what identifies the
 * sun draw; relying on the call order alone would also match the sunrise fan.</p>
 */
@Mixin(LevelRenderer.class)
public abstract class DreamSunMixin {
    private static final String UPLOAD =
            "Lcom/mojang/blaze3d/vertex/BufferUploader;end(Lcom/mojang/blaze3d/vertex/BufferBuilder;)V";

    @Redirect(method = "renderSky",
            at = @At(value = "INVOKE", target = UPLOAD, ordinal = 0),
            slice = @Slice(from = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;SUN_LOCATION:"
                            + "Lnet/minecraft/resources/ResourceLocation;")))
    private void dreamworld$hideSun(BufferBuilder buffer) {
        if (DreamClientState.isDreaming()) {
            return;
        }
        BufferUploader.end(buffer);
    }
}
