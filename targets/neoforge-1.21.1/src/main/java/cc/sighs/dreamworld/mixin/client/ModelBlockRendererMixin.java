package cc.sighs.dreamworld.mixin.client;

import cc.sighs.dreamworld.client.DreamBlockTint;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Per-block-face dream tint.
 *
 * <p>{@link ModelBlockRenderer#putQuadData} computes a face's colour right before writing its
 * vertices. Multiplying those factors by the block's own bright pink turns the terrain itself pink
 * (every face of every block), with a deterministic shade per block type. The light array is
 * replaced with full brightness so the pink stays bright even when the server clock says night.</p>
 */
@Mixin(ModelBlockRenderer.class)
public abstract class ModelBlockRendererMixin {
    private static final String PUT_BULK_DATA =
            "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData("
                    + "Lcom/mojang/blaze3d/vertex/PoseStack$Pose;"
                    + "Lnet/minecraft/client/renderer/block/model/BakedQuad;"
                    + "[FFFFF[IIZ)V";

    @ModifyArg(method = "putQuadData",
            at = @At(value = "INVOKE", target = PUT_BULK_DATA),
            index = 2)
    private float[] dreamworld$brighten(float[] brightness) {
        return DreamBlockTint.active() ? new float[]{1.0F, 1.0F, 1.0F, 1.0F} : brightness;
    }

    @ModifyArg(method = "putQuadData",
            at = @At(value = "INVOKE", target = PUT_BULK_DATA),
            index = 3)
    private float dreamworld$tintRed(float red) {
        return DreamBlockTint.active() ? red * DreamBlockTint.red() : red;
    }

    @ModifyArg(method = "putQuadData",
            at = @At(value = "INVOKE", target = PUT_BULK_DATA),
            index = 4)
    private float dreamworld$tintGreen(float green) {
        return DreamBlockTint.active() ? green * DreamBlockTint.green() : green;
    }

    @ModifyArg(method = "putQuadData",
            at = @At(value = "INVOKE", target = PUT_BULK_DATA),
            index = 5)
    private float dreamworld$tintBlue(float blue) {
        return DreamBlockTint.active() ? blue * DreamBlockTint.blue() : blue;
    }
}
