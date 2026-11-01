package cc.sighs.dreamworld.client;

import cc.sighs.dreamworld.DreamWorld;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.resources.Identifier;

/**
 * Applies the dream palette through the vanilla block tint pipeline.
 *
 * <p>The 1.21 family hooked the block model renderer to rewrite the base colour of every quad. 26.1
 * computes a quad's colour from the block's registered {@link BlockTintSource}s instead, with the
 * world and position available, so the palette is registered as a tint source that wraps whatever
 * vanilla source the block already had.</p>
 *
 * <p>Water keeps its own palette: a lake should read as its own material instead of a pink puddle,
 * which is why fluid blocks use the water profile.</p>
 */
public final class DreamBlockTintSource implements BlockTintSource {
    /** Key of the shared palette switch: any block whose id stays tintable uses this. */
    private static final Identifier ID = Identifier.fromNamespaceAndPath(DreamWorld.MOD_ID, "dream_tint");

    private final BlockTintSource wrapped;
    private final boolean water;

    public DreamBlockTintSource(BlockTintSource wrapped, boolean water) {
        this.wrapped = wrapped;
        this.water = water;
    }

    @Override
    public int color(BlockState state) {
        // Outside a level there is no dream layer to tint; keep the vanilla colour.
        return this.wrapped == null ? -1 : this.wrapped.color(state);
    }

    @Override
    public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        int vanilla = this.wrapped == null ? -1 : this.wrapped.colorInWorld(state, level, pos);
        if (!DreamClientState.isDreaming() || DreamClientPatches.isIntruded(pos)) {
            return vanilla;
        }
        float red = ((vanilla >> 16) & 0xFF) / 255.0F;
        float green = ((vanilla >> 8) & 0xFF) / 255.0F;
        float blue = (vanilla & 0xFF) / 255.0F;
        float alpha = ((vanilla >> 24) & 0xFF) / 255.0F;
        if (alpha == 0.0F) {
            alpha = 1.0F;
        }
        float[] tinted = this.water
                ? DreamBlockTint.transformWater(red, green, blue)
                : DreamBlockTint.transformFor(state, red, green, blue);
        return ARGB.color(toByte(alpha), toByte(tinted[0]), toByte(tinted[1]), toByte(tinted[2]));
    }

    @Override
    public int colorAsTerrainParticle(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        return this.colorInWorld(state, level, pos);
    }

    /** {@return whether the block is drawn with the water palette} */
    public static boolean isWater(BlockState state) {
        return state.is(Blocks.WATER) || state.is(Blocks.BUBBLE_COLUMN);
    }

    private static int toByte(float value) {
        return Math.round(Math.max(0.0F, Math.min(1.0F, value)) * 255.0F);
    }
}
