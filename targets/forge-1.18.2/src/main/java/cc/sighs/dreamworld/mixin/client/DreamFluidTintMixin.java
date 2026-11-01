package cc.sighs.dreamworld.mixin.client;

import cc.sighs.dreamworld.client.DreamBlockTint;
import cc.sighs.dreamworld.client.DreamClientPatches;
import cc.sighs.dreamworld.client.DreamClientState;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.fluids.FluidAttributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Tints fluids on the dream layer.
 *
 * <p>Fluids are not drawn through the block model renderer, so the per-face block tint never reaches
 * them. {@code tesselate} asks the fluid attributes for one tint colour and then paints the whole
 * surface with it, which is the single place water, lava and every modded fluid get their colour
 * from - so the dream palette is applied here instead, with the same pipeline the blocks use.</p>
 *
 * <p>A fluid that sits on an intruded block is left alone, matching the rule for intruded blocks
 * themselves: it came from the other layer and keeps its colour.</p>
 */
@Mixin(LiquidBlockRenderer.class)
public abstract class DreamFluidTintMixin {
    private static final String GET_TINT_COLOR =
            "Lnet/minecraftforge/fluids/FluidAttributes;getColor("
                    + "Lnet/minecraft/world/level/BlockAndTintGetter;"
                    + "Lnet/minecraft/core/BlockPos;)I";

    @Redirect(method = "tesselate", at = @At(value = "INVOKE", target = GET_TINT_COLOR), remap = false)
    private int dreamworld$tintFluid(FluidAttributes attributes, BlockAndTintGetter level,
            BlockPos pos) {
        int colour = attributes.getColor(level, pos);
        if (!DreamClientState.isDreaming() || DreamClientPatches.isIntruded(pos)) {
            return colour;
        }
        return DreamBlockTint.tintFluid(colour);
    }
}
