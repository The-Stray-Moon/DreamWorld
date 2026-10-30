package cc.sighs.dreamworld.client;

import net.minecraft.world.level.block.state.BlockState;

/**
 * Per-block dream colour pipeline.
 *
 * <p>For every block face the original colour is first hue-rotated to pink and brightened, then
 * multiplied by a per-block-type weighted pink blend. The result is written into the quad's vertex
 * colour during chunk mesh building.</p>
 */
public final class DreamBlockTint {
    /** Pink hue in [0, 1): 330 degrees. */
    private static final float PINK_HUE = 330.0F / 360.0F;
    /** Minimum saturation after the hue rotation. */
    private static final float PINK_SATURATION = 0.62F;
    /** Brightness boost applied after the rotation. */
    private static final float BRIGHTNESS_BOOST = 1.28F;

    /** Second stage: the colour the rotated result is multiplied by. */
    private static final float BLEND_RED = 1.0F;
    private static final float BLEND_GREEN = 0.62F;
    private static final float BLEND_BLUE = 0.88F;
    private static final float MIN_WEIGHT = 0.30F;
    private static final float MAX_WEIGHT = 0.65F;
    private static final float HUE_SPREAD = 0.06F;

    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<float[]> BLEND =
            ThreadLocal.withInitial(() -> new float[]{1.0F, 1.0F, 1.0F});

    private DreamBlockTint() {
    }

    public static void begin(BlockState state) {
        DEPTH.set(DEPTH.get() + 1);
        BLEND.set(blend(state));
    }

    public static void end() {
        DEPTH.set(DEPTH.get() - 1);
    }

    public static boolean active() {
        return DEPTH.get() > 0 && DreamClientState.isDreaming();
    }

    /**
     * Runs the pipeline for one quad colour: hue-rotate to pink, brighten, then multiply by the
     * per-block blend. Returns the three factors written into the vertex colour.
     */
    public static float[] transform(float red, float green, float blue) {
        float[] hsv = toHsv(red, green, blue);
        hsv[0] = PINK_HUE;
        hsv[1] = Math.max(hsv[1], PINK_SATURATION);
        hsv[2] = clamp(hsv[2] * BRIGHTNESS_BOOST);
        float[] rotated = toRgb(hsv);
        float[] blend = BLEND.get();
        return new float[]{
                clamp(rotated[0] * blend[0]),
                clamp(rotated[1] * blend[1]),
                clamp(rotated[2] * blend[2])
        };
    }

    private static float[] blend(BlockState state) {
        if (state == null) {
            return new float[]{BLEND_RED, BLEND_GREEN, BLEND_BLUE};
        }
        int hash = state.getBlock().hashCode() * 31 + state.hashCode();
        float weightNoise = ((hash >>> 8) & 0xFF) / 255.0F;
        float hueNoise = (hash & 0xFF) / 255.0F;
        float weight = MIN_WEIGHT + weightNoise * (MAX_WEIGHT - MIN_WEIGHT);
        float green = BLEND_GREEN + (hueNoise - 0.5F) * HUE_SPREAD;
        float blue = BLEND_BLUE + (weightNoise - 0.5F) * HUE_SPREAD;
        return new float[]{
                lerp(1.0F, BLEND_RED, weight),
                lerp(1.0F, clamp(green), weight),
                lerp(1.0F, clamp(blue), weight)
        };
    }

    private static float[] toHsv(float red, float green, float blue) {
        float max = Math.max(red, Math.max(green, blue));
        float min = Math.min(red, Math.min(green, blue));
        float delta = max - min;
        float hue;
        if (delta < 1.0E-6F) {
            hue = 0.0F;
        } else if (max == red) {
            hue = ((green - blue) / delta) / 6.0F;
        } else if (max == green) {
            hue = (2.0F + (blue - red) / delta) / 6.0F;
        } else {
            hue = (4.0F + (red - green) / delta) / 6.0F;
        }
        if (hue < 0.0F) {
            hue += 1.0F;
        }
        float saturation = max < 1.0E-6F ? 0.0F : delta / max;
        return new float[]{hue, saturation, max};
    }

    private static float[] toRgb(float[] hsv) {
        float hue = hsv[0];
        float saturation = hsv[1];
        float value = hsv[2];
        float chroma = value * saturation;
        float section = hue * 6.0F;
        float x = chroma * (1.0F - Math.abs(section % 2.0F - 1.0F));
        float r;
        float g;
        float b;
        switch ((int) Math.floor(section) % 6) {
            case 0 -> { r = chroma; g = x; b = 0.0F; }
            case 1 -> { r = x; g = chroma; b = 0.0F; }
            case 2 -> { r = 0.0F; g = chroma; b = x; }
            case 3 -> { r = 0.0F; g = x; b = chroma; }
            case 4 -> { r = x; g = 0.0F; b = chroma; }
            default -> { r = chroma; g = 0.0F; b = x; }
        }
        float m = value - chroma;
        return new float[]{r + m, g + m, b + m};
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
