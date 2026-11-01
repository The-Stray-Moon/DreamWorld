package cc.sighs.dreamworld.mixin.client;

import cc.sighs.dreamworld.client.DreamBlockTint;
import cc.sighs.dreamworld.client.DreamClientPatches;
import cc.sighs.dreamworld.client.DreamClientState;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Tints water on the dream layer. Vanilla asks {@link BiomeColors} for the single tint colour the
 * whole surface is painted with, which is the one place the dream palette can be applied to fluids.
 */
@Mixin(LiquidBlockRenderer.class)
public abstract class DreamFluidTintMixin {
    @Redirect(method = "tesselate",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/BiomeColors;getAverageWaterColor"
                            + "(Lnet/minecraft/world/level/BlockAndTintGetter;"
                            + "Lnet/minecraft/core/BlockPos;)I"))
    private int dreamworld$tintFluid(BlockAndTintGetter level, BlockPos pos) {
        int colour = BiomeColors.getAverageWaterColor(level, pos);
        if (!DreamClientState.isDreaming() || DreamClientPatches.isIntruded(pos)) {
            return colour;
        }
        return DreamBlockTint.tintFluid(colour);
    }
}
