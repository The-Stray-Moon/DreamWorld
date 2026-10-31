package cc.sighs.dreamworld.mixin.client;

import cc.sighs.dreamworld.client.DreamClientState;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.MeshData;
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
 * <p>The slice anchors on the first shader texture bind of the method, which is the sun: relying on
 * the call order alone would break as soon as the sunrise fan were inlined differently, whereas the
 * texture bind is exactly what identifies the sun draw. The mesh is closed when the draw is skipped,
 * otherwise its vertex buffer would never be released.</p>
 */
@Mixin(LevelRenderer.class)
public abstract class DreamSunMixin {
    private static final String DRAW_WITH_SHADER =
            "Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/MeshData;)V";
    private static final String BIND_TEXTURE =
            "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(ILnet/minecraft/resources/ResourceLocation;)V";

    @Redirect(method = "renderSky",
            at = @At(value = "INVOKE", target = DRAW_WITH_SHADER, ordinal = 0),
            slice = @Slice(from = @At(value = "INVOKE", target = BIND_TEXTURE)))
    private void dreamworld$hideSun(MeshData mesh) {
        if (DreamClientState.isDreaming()) {
            mesh.close();
            return;
        }
        BufferUploader.drawWithShader(mesh);
    }
}
