package cc.sighs.dreamworld.client;

import cc.sighs.dreamworld.spi.DreamPlatform;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Dream palette for blocks and fluids.
 *
 * <p>The pipeline has two steps. First the colour is hue-rotated onto the palette hue, which lands
 * every material on the same hue. Then the per-type profile varies it: the hue gets a small jitter of
 * its own and the saturation and brightness are scaled by values drawn from the type's hash, which
 * gives every material its own shade without turning into per-block noise.</p>
 *
 * <p>Terrain and water sit on different palettes. Blocks keep the pink of the dream layer; water uses
 * its own scheme colour, rgb(62, 117, 134), with a softer saturation, so a lake reads as its own
 * material instead of a pink puddle.</p>
 *
 * <p>The tint is applied to the base colour of a face and the shading is multiplied on top of it
 * afterwards, which keeps a tinted block reading as a lit surface instead of a flat silhouette. An
 * intruded block is skipped: it came from the other layer and keeps its own colour.</p>
 */
public final class DreamBlockTint {
    /** Hue every block face is rotated to, in [0, 1): 330 degrees. */
    private static final float BLOCK_HUE = 330.0F / 360.0F;
    /** Saturation blocks are lifted to; darker faces keep their own if it is higher. */
    private static final float BLOCK_SATURATION = 0.62F;
    /** Brightness the colour is pushed to, before the per-type variation. */
    private static final float DREAM_BRIGHTNESS = 1.25F;

    /** Scheme colour the water palette is derived from: rgb(62, 117, 134). */
    private static final Vec3 WATER_COLOR =
            new Vec3(62.0D / 255.0D, 117.0D / 255.0D, 134.0D / 255.0D);
    /** Hue of the water palette, derived from rgb(62, 117, 134). */
    private static final float WATER_HUE = hueOf(WATER_COLOR);
    /** Water is pulled a little further down in saturation than the terrain. */
    private static final float MIN_WATER_SATURATION_SCALE = 0.72F;
    private static final float MAX_WATER_SATURATION_SCALE = 0.88F;

    /** How far a type's hue may wander around its palette hue, in [0, 1) hue units. */
    private static final float HUE_JITTER = 0.020F;
    /** Per-type saturation multiplier spread. */
    private static final float MIN_SATURATION_SCALE = 0.75F;
    private static final float MAX_SATURATION_SCALE = 0.92F;
    /** Per-type brightness multiplier spread. The top end pushes the colour towards white. */
    private static final float MIN_BRIGHTNESS_SCALE = 1.00F;
    private static final float MAX_BRIGHTNESS_SCALE = 1.55F;

    /**
     * The random variation of one type.
     *
     * @param hue             the hue this type lands on
     * @param saturationFloor saturation the colour is lifted to
     * @param saturation      multiplier applied on top of that floor
     * @param brightness      multiplier applied to the final colour
     */
    private record Profile(float hue, float saturationFloor, float saturation, float brightness) {
    }

    private static final Profile NEUTRAL =
            new Profile(BLOCK_HUE, BLOCK_SATURATION, 1.0F, 1.0F);

    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Profile> PROFILE = ThreadLocal.withInitial(() -> NEUTRAL);
    private static final ThreadLocal<Boolean> TINTABLE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private DreamBlockTint() {
    }

    /**
     * Starts tinting one batched block.
     *
     * @param pos the block position, used to recognise a block that an intrusion carried in
     */
    public static void begin(BlockState state, BlockPos pos) {
        DEPTH.set(DEPTH.get() + 1);
        boolean tintable = DreamClientState.isDreaming() && !DreamClientPatches.isIntruded(pos);
        TINTABLE.set(tintable);
        PROFILE.set(tintable ? profile(state) : NEUTRAL);
    }

    public static void end() {
        DEPTH.set(DEPTH.get() - 1);
    }

    public static boolean active() {
        return DEPTH.get() > 0 && TINTABLE.get();
    }

    /**
     * Tints one face colour and returns the three factors to write into the vertex colour.
     *
     * <p>Step one rotates the face's hue onto its type's palette hue, which leaves every material on
     * the same palette while its own saturation and value still feed the result. Step two is the
     * type's random variation. The result is a base colour - the vertex shading is applied on top of
     * it by the renderer, so this runs before the shadows instead of after them.</p>
     */
    public static float[] transform(float red, float green, float blue) {
        return apply(PROFILE.get(), red, green, blue);
    }

    /** Tints one colour with the profile of {@code state}, without the renderer's thread state. */
    public static float[] transformFor(BlockState state, float red, float green, float blue) {
        return apply(profile(state), red, green, blue);
    }

    /** Tints one colour with the water palette. */
    public static float[] transformWater(float red, float green, float blue) {
        return apply(waterProfile(), red, green, blue);
    }

    /**
     * Tints a fluid colour, keeping its alpha.
     *
     * <p>Fluids never go through the block model renderer, so they need their own entry point. Water
     * belongs to the sky palette rather than the terrain one: it is hue-rotated onto the sky's hue and
     * its saturation is pulled down, so a lake blends into the sky instead of sitting on the ground as
     * a pink puddle.</p>
     */
    public static int tintFluid(int argb) {
        float alpha = ((argb >> 24) & 0xFF) / 255.0F;
        float red = ((argb >> 16) & 0xFF) / 255.0F;
        float green = ((argb >> 8) & 0xFF) / 255.0F;
        float blue = (argb & 0xFF) / 255.0F;
        float[] tinted = apply(waterProfile(), red, green, blue);
        return ARGB.color(toByte(alpha), toByte(tinted[0]), toByte(tinted[1]),
                toByte(tinted[2]));
    }

    private static float[] apply(Profile profile, float red, float green, float blue) {
        float[] hsv = toHsv(red, green, blue);
        float hue = hueRotate(hsv[0], profile.hue());
        float saturation = clamp(Math.max(hsv[1], profile.saturationFloor()) * profile.saturation());
        float value = clamp(hsv[2] * DREAM_BRIGHTNESS);
        float[] tinted = toRgb(hue, saturation, value);
        // Brightness is applied to the finished colour rather than to the HSV value: a value above
        // one clamps to one inside the HSV round trip and would do nothing, while multiplying here
        // still lifts the channels towards white - a paler, brighter shade.
        float brightness = profile.brightness();
        return new float[]{
                clamp(tinted[0] * brightness),
                clamp(tinted[1] * brightness),
                clamp(tinted[2] * brightness)
        };
    }

    private static int toByte(float value) {
        return Math.round(clamp(value) * 255.0F);
    }

    /** {@return the hue of a colour, in [0, 1)} */
    private static float hueOf(Vec3 colour) {
        return toHsv((float) colour.x, (float) colour.y, (float) colour.z)[0];
    }

    /**
     * Rotates a face's hue onto the palette hue of its type.
     *
     * <p>The rotation angle is the distance from this face's own hue to the target, so every
     * material lands on that hue. A fixed angle would send each colour to a different result
     * instead, which is not what a single-palette dream layer needs.</p>
     */
    private static float hueRotate(float hue, float target) {
        float delta = target - hue;
        return wrapHue(hue + delta);
    }

    /** Builds the stable variation of a block type from its hash. */
    private static Profile profile(BlockState state) {
        if (state == null) {
            return NEUTRAL;
        }
        int hash = state.getBlock().hashCode() * 31 + state.hashCode();
        float hue = terrainHue() + (noise(hash, 3) - 0.5F) * 2.0F * HUE_JITTER;
        return new Profile(
                wrapHue(hue),
                BLOCK_SATURATION,
                lerp(MIN_SATURATION_SCALE, MAX_SATURATION_SCALE, noise(hash, 7)),
                lerp(MIN_BRIGHTNESS_SCALE, MAX_BRIGHTNESS_SCALE, noise(hash, 13)));
    }

    /**
     * The water palette: its own scheme colour with a softer saturation.
     *
     * <p>Water is a single material, so the spread comes from the water block's own hash like any
     * other type; only the hue and the saturation band differ.</p>
     */
    private static Profile waterProfile() {
        int hash = Blocks.WATER.hashCode();
        float hue = waterHue() + (noise(hash, 3) - 0.5F) * 2.0F * HUE_JITTER;
        return new Profile(
                wrapHue(hue),
                BLOCK_SATURATION,
                lerp(MIN_WATER_SATURATION_SCALE, MAX_WATER_SATURATION_SCALE, noise(hash, 7)),
                lerp(MIN_BRIGHTNESS_SCALE, MAX_BRIGHTNESS_SCALE, noise(hash, 13)));
    }

    /** {@return the hue the terrain draws with, honouring the palette swap switch} */
    private static float terrainHue() {
        return DreamPlatform.get().config().swapPalettes() ? WATER_HUE : BLOCK_HUE;
    }

    /** {@return the hue the water draws with, honouring the palette swap switch} */
    private static float waterHue() {
        return DreamPlatform.get().config().swapPalettes() ? BLOCK_HUE : WATER_HUE;
    }

    /**
     * Colour the sky draws with.
     *
     * <p>The sky belongs to the water palette - the scheme colour it was chosen from is the same one
     * the water palette is derived from, which is what makes a dream lake blend into the sky. Only
     * the hue moves: the saturation and lightness stay those of the scheme sky, so the palette swap
     * turns the sky pink together with the water instead of leaving a blue sky over a pink lake.</p>
     */
    public static Vec3 skyColor() {
        float[] hsv = toHsv((float) DreamClientState.SKY_COLOR.x,
                (float) DreamClientState.SKY_COLOR.y,
                (float) DreamClientState.SKY_COLOR.z);
        float[] rgb = toRgb(waterHue(), hsv[1], hsv[2]);
        return new Vec3(rgb[0], rgb[1], rgb[2]);
    }

    /** Deterministic [0, 1) noise for one channel of a block type hash. */
    private static float noise(int hash, int salt) {
        int mixed = hash * 0x9E3779B1 + salt * 0x85EBCA77;
        mixed ^= mixed >>> 15;
        mixed *= 0x2545F491;
        mixed ^= mixed >>> 13;
        return (mixed & 0xFFFFFF) / (float) 0x1000000;
    }

    private static float wrapHue(float hue) {
        float wrapped = hue % 1.0F;
        return wrapped < 0.0F ? wrapped + 1.0F : wrapped;
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

    private static float[] toRgb(float hue, float saturation, float value) {
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
