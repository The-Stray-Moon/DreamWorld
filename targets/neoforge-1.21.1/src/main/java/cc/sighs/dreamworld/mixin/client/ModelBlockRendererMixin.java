package cc.sighs.dreamworld.mixin.client;

import cc.sighs.dreamworld.client.DreamBlockTint;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Per-block-face dream colour, applied before the shading.
 *
 * <p>{@code putQuadData} hands the vertex writer three things that matter: the per-vertex shading
 * array, the base colour and the lightmap. The tint rewrites only the base colour, so the shading is
 * still multiplied on top of it by {@code putBulkData} - the face is tinted first and lit afterwards,
 * which keeps the result reading as a surface instead of a flat silhouette. Baking the tint into
 * the shading array, as the first version did, threw the shadows away entirely.</p>
 */
@Mixin(ModelBlockRenderer.class)
public abstract class ModelBlockRendererMixin {
    /** The base colour arguments of the vertex write: red, green, blue. */
    private static final int ARG_RED = 3;
    private static final int ARG_GREEN = 4;
    private static final int ARG_BLUE = 5;

    private static final String PUT_BULK_DATA =
            "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData("
                    + "Lcom/mojang/blaze3d/vertex/PoseStack$Pose;"
                    + "Lnet/minecraft/client/renderer/block/model/BakedQuad;"
                    + "[FFFFF[IIZ)V";

    @ModifyArgs(method = "putQuadData",
            at = @At(value = "INVOKE", target = PUT_BULK_DATA))
    private void dreamworld$tint(Args args) {
        if (!DreamBlockTint.active()) {
            return;
        }
        float[] colour = DreamBlockTint.transform(args.get(ARG_RED), args.get(ARG_GREEN),
                args.get(ARG_BLUE));
        args.set(ARG_RED, colour[0]);
        args.set(ARG_GREEN, colour[1]);
        args.set(ARG_BLUE, colour[2]);
    }
}
