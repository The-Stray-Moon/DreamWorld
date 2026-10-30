package cc.sighs.dreamworld.mixin.client;

import cc.sighs.dreamworld.client.DreamClientState;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Per-face dream tint.
 *
 * <p>{@link ModelBlockRenderer#putQuadData} is where a block face's final colour is computed right
 * before its vertices are written into the chunk mesh. Mixing the dream colour into that value
 * tints the block geometry itself (each face of every block), not a screen overlay and not a
 * whole-layer post pass. The chunk mesh is rebuilt when the dream state changes, so the tint never
 * leaks into the real world.</p>
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
            index = 3)
    private float dreamworld$tintRed(float red) {
        return DreamClientState.isDreaming() ? red : red;
    }

    @ModifyArg(method = "putQuadData",
            at = @At(value = "INVOKE", target = PUT_BULK_DATA),
            index = 4)
    private float dreamworld$tintGreen(float green) {
        return DreamClientState.isDreaming() ? green * 0.55F : green;
    }

    @ModifyArg(method = "putQuadData",
            at = @At(value = "INVOKE", target = PUT_BULK_DATA),
            index = 5)
    private float dreamworld$tintBlue(float blue) {
        return DreamClientState.isDreaming() ? blue * 0.75F : blue;
    }
}
