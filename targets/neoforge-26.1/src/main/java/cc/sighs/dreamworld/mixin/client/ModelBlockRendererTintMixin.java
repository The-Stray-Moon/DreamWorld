package cc.sighs.dreamworld.mixin.client;

import cc.sighs.dreamworld.client.DreamClientPatches;
import cc.sighs.dreamworld.client.DreamClientState;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives every block quad a tint index while dreaming.
 *
 * <p>26.1 only consults {@code BlockTintSource}s for quads whose model carries a tint index.
 * Grass and leaves have one, which is why they already tinted; stone, packed ice and most other
 * blocks have none. Rebuilding the quad with tint index 0 routes it through the palette source
 * without changing its geometry or UVs.</p>
 */
@Mixin(ModelBlockRenderer.class)
public abstract class ModelBlockRendererTintMixin {
    @Shadow
    private QuadInstance quadInstance;

    @Inject(method = "putQuadWithTint", at = @At("HEAD"), cancellable = true)
    private void dreamworld$tintUntintedQuad(BlockQuadOutput output, float x, float y, float z,
            BlockAndTintGetter level, BlockState state, BlockPos pos, BakedQuad quad,
            CallbackInfo ci) {
        if (level == null || pos == null || quad.materialInfo().tintIndex() != -1
                || !DreamClientState.isDreaming() || DreamClientPatches.isIntruded(pos)) {
            return;
        }
        BlockTintSource source = Minecraft.getInstance().getBlockColors().getTintSource(state, 0);
        if (source == null) {
            return;
        }
        this.quadInstance.multiplyColor(source.colorInWorld(state, level, pos));
        output.put(x, y, z, withTintIndex(quad), this.quadInstance);
        ci.cancel();
    }

    private static BakedQuad withTintIndex(BakedQuad quad) {
        BakedQuad.MaterialInfo info = quad.materialInfo();
        BakedQuad.MaterialInfo tinted = new BakedQuad.MaterialInfo(info.sprite(), info.layer(),
                info.itemRenderType(), 0, info.shade(), info.lightEmission());
        return new BakedQuad(quad.position0(), quad.position1(), quad.position2(), quad.position3(),
                quad.packedUV0(), quad.packedUV1(), quad.packedUV2(), quad.packedUV3(),
                quad.direction(), tinted);
    }
}
