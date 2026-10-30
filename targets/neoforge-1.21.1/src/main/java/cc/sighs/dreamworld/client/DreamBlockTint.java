package cc.sighs.dreamworld.client;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Per-block dream tint.
 *
 * <p>While the local player dreams, every block face is recoloured by blending its base colour
 * towards a bright pink with a per-block-type random weight, instead of multiplying by a fixed
 * dark factor: light textures stay bright, and each block type lands on its own pink shade. The
 * weight is deterministic per block state, so a given block always looks the same.</p>
 */
public final class DreamBlockTint {
    /** Bright pink the block colour is blended towards: rgb(255, 115, 204). */
    private static final float PINK_RED = 1.0F;
    private static final float PINK_GREEN = 0.45F;
    private static final float PINK_BLUE = 0.80F;

    /** Blend weight range: how far a block is pulled towards the pink. */
    private static final float MIN_WEIGHT = 0.55F;
    private static final float MAX_WEIGHT = 1.0F;
    /** Extra per-block spread on green/blue so neighbouring block types stay distinguishable. */
    private static final float HUE_SPREAD = 0.08F;

    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<float[]> PINK =
            ThreadLocal.withInitial(() -> new float[]{1.0F, 1.0F, 1.0F});

    private DreamBlockTint() {
    }

    /** Called by the block render dispatcher around a chunk batch. */
    public static void begin(BlockState state) {
        DEPTH.set(DEPTH.get() + 1);
        PINK.set(mix(state));
    }

    public static void end() {
        DEPTH.set(DEPTH.get() - 1);
    }

    /** True only while a chunk block batch is being built and the player is dreaming. */
    public static boolean active() {
        return DEPTH.get() > 0 && DreamClientState.isDreaming();
    }

    public static float red() {
        return PINK.get()[0];
    }

    public static float green() {
        return PINK.get()[1];
    }

    public static float blue() {
        return PINK.get()[2];
    }

    /**
     * Weighted blend between the untouched colour (1, 1, 1) and the bright pink. The weight and the
     * hue jitter come from the block state hash, so the result is stable per block type.
     */
    public static float[] mix(BlockState state) {
        if (state == null) {
            return new float[]{PINK_RED, PINK_GREEN, PINK_BLUE};
        }
        int hash = state.getBlock().hashCode() * 31 + state.hashCode();
        float weightNoise = ((hash >>> 8) & 0xFF) / 255.0F;
        float hueNoise = (hash & 0xFF) / 255.0F;
        float weight = MIN_WEIGHT + weightNoise * (MAX_WEIGHT - MIN_WEIGHT);
        float green = PINK_GREEN + (hueNoise - 0.5F) * HUE_SPREAD;
        float blue = PINK_BLUE + (weightNoise - 0.5F) * HUE_SPREAD;

        return new float[]{
                blend(1.0F, PINK_RED, weight),
                blend(1.0F, clamp(green), weight),
                blend(1.0F, clamp(blue), weight)
        };
    }

    private static float blend(float base, float target, float weight) {
        return base + (target - base) * weight;
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    /** Same colour as a vector, for callers that prefer it. */
    public static Vec3 mixVector(BlockState state) {
        float[] pink = mix(state);
        return new Vec3(pink[0], pink[1], pink[2]);
    }
}
