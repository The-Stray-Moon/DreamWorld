package cc.sighs.dreamworld.client;

import cc.sighs.dreamworld.DreamWorld;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.fluid.FluidTintSource;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * NeoForge 26.1 fluid tint adapter.
 *
 * <p>NeoForge replaces the vanilla {@code BlockTintSource} on {@code FluidModel} with its own
 * {@link FluidTintSource}, so the ordinary block wrapper does not see water. This wraps the
 * original source and applies the dream water palette to every entry point the fluid renderer and
 * the fluid item models use.</p>
 */
public final class DreamFluidTintSource implements FluidTintSource {
    private final FluidTintSource wrapped;

    public DreamFluidTintSource(FluidTintSource wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public int color(FluidState state) {
        return tint(this.wrapped == null ? -1 : this.wrapped.color(state));
    }

    @Override
    public int colorInWorld(FluidState state, BlockState blockState, BlockAndTintGetter level,
            BlockPos pos) {
        return tint(this.wrapped == null ? -1
                : this.wrapped.colorInWorld(state, blockState, level, pos), pos);
    }

    @Override
    public int colorAsStack(FluidStack stack) {
        return tint(this.wrapped == null ? -1 : this.wrapped.colorAsStack(stack));
    }

    @Override
    public int color(BlockState state) {
        return tint(this.wrapped == null ? -1 : this.wrapped.color(state));
    }

    @Override
    public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        return tint(this.wrapped == null ? -1 : this.wrapped.colorInWorld(state, level, pos), pos);
    }

    private static int tint(int argb) {
        if (!DreamClientState.isDreaming()) {
            return argb;
        }
        return apply(argb);
    }

    private static int tint(int argb, BlockPos pos) {
        if (!DreamClientState.isDreaming() || DreamClientPatches.isIntruded(pos)) {
            return argb;
        }
        return apply(argb);
    }

    private static int apply(int argb) {
        float alpha = ((argb >> 24) & 0xFF) / 255.0F;
        float red = ((argb >> 16) & 0xFF) / 255.0F;
        float green = ((argb >> 8) & 0xFF) / 255.0F;
        float blue = (argb & 0xFF) / 255.0F;
        if (alpha == 0.0F) {
            alpha = 1.0F;
        }
        float[] tinted = DreamBlockTint.transformWater(red, green, blue);
        return ARGB.color(toByte(alpha), toByte(tinted[0]), toByte(tinted[1]), toByte(tinted[2]));
    }

    private static int toByte(float value) {
        return Math.round(Math.max(0.0F, Math.min(1.0F, value)) * 255.0F);
    }
}
