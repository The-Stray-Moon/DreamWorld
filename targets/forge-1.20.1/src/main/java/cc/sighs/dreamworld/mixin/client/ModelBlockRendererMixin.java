package cc.sighs.dreamworld.mixin.client;

import cc.sighs.dreamworld.client.DreamBlockTint;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Per-block-face dream colour, applied before the shading.
 *
 * <p>{@code putQuadData} hands the vertex writer three things that matter: the per-vertex shading
 * array, the base colour and the lightmap. The tint rewrites only the base colour, so the shading is
 * still multiplied on top of it by {@code putBulkData} - the face is tinted first and lit afterwards,
 * which keeps the result reading as a surface instead of a flat silhouette. Baking the tint into
 * the shading array, as the first version did, threw the shadows away entirely.</p>
 *
 * <p>This uses a redirect rather than {@code @ModifyArgs}: Mixin's generated {@code Args} class lives
 * in a synthetic package that Forge's module layer does not export, which crashes the client while
 * {@code BlockRenderDispatcher} is being constructed.</p>
 */
@Mixin(ModelBlockRenderer.class)
public abstract class ModelBlockRendererMixin {
    private static final String PUT_BULK_DATA =
            "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData("
                    + "Lcom/mojang/blaze3d/vertex/PoseStack$Pose;"
                    + "Lnet/minecraft/client/renderer/block/model/BakedQuad;"
                    + "[FFFF[IIZ)V";

    @Redirect(method = "putQuadData",
            at = @At(value = "INVOKE", target = PUT_BULK_DATA))
    private void dreamworld$tint(VertexConsumer consumer, PoseStack.Pose pose, BakedQuad quad,
            float[] colour, float red, float green, float blue, int[] light, int overlay,
            boolean readExisting) {
        if (DreamBlockTint.active()) {
            float[] tinted = DreamBlockTint.transform(red, green, blue);
            red = tinted[0];
            green = tinted[1];
            blue = tinted[2];
        }
        consumer.putBulkData(pose, quad, colour, red, green, blue, light, overlay, readExisting);
    }
}
