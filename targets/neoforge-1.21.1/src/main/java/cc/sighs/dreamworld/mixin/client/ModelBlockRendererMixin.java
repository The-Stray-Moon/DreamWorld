package cc.sighs.dreamworld.mixin.client;

import cc.sighs.dreamworld.client.DreamBlockTint;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Per-block-face dream colour, applied before the shading. */
@Mixin(ModelBlockRenderer.class)
public abstract class ModelBlockRendererMixin {
    private static final String PUT_BULK_DATA =
            "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData("
                    + "Lcom/mojang/blaze3d/vertex/PoseStack$Pose;"
                    + "Lnet/minecraft/client/renderer/block/model/BakedQuad;"
                    + "[FFFFF[IIZ)V";

    @Redirect(method = "putQuadData",
            at = @At(value = "INVOKE", target = PUT_BULK_DATA))
    private void dreamworld$tint(VertexConsumer consumer, PoseStack.Pose pose, BakedQuad quad,
            float[] colour, float red, float green, float blue, float alpha, int[] light,
            int overlay, boolean readExisting) {
        if (DreamBlockTint.active()) {
            float[] tinted = DreamBlockTint.transform(red, green, blue);
            red = tinted[0];
            green = tinted[1];
            blue = tinted[2];
        }
        consumer.putBulkData(pose, quad, colour, red, green, blue, alpha, light, overlay,
                readExisting);
    }
}
