package cc.sighs.dreamworld.mixin.client;

import cc.sighs.dreamworld.client.DreamBlockTint;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Per-block-face dream colour.
 *
 * <p>Rewrites the colour arguments of {@code putQuadData}'s vertex write: the brightness array is
 * lifted to full and the three colour factors are replaced by {@link DreamBlockTint#transform}
 * (hue-rotate to pink, brighten, multiply by the per-block blend). This runs while the chunk mesh is
 * built, so each block face is coloured individually.</p>
 */
@Mixin(ModelBlockRenderer.class)
public abstract class ModelBlockRendererMixin {
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
        args.set(2, new float[]{1.0F, 1.0F, 1.0F, 1.0F});
        float[] colour = DreamBlockTint.transform(args.get(3), args.get(4), args.get(5));
        args.set(3, colour[0]);
        args.set(4, colour[1]);
        args.set(5, colour[2]);
    }
}
