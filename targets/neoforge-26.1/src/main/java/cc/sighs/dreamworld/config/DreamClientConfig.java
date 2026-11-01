package cc.sighs.dreamworld.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-side settings of the dream layer.
 *
 * <p>Everything here only affects how the dream is drawn, so it lives in a client config: a
 * dedicated server never reads it and the file stays per-installation.</p>
 */
public final class DreamClientConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue SWAP_PALETTES;
    private static final ModConfigSpec.IntValue AMBIENT_SKY_LIGHT;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        SWAP_PALETTES = builder
                .comment("Swap the hue wheels of the terrain and the water.",
                        "Terrain normally uses the pink wheel and water the blue-green one;",
                        "enabling this draws the terrain blue-green and the water pink.")
                .define("swapPalettes", false);
        AMBIENT_SKY_LIGHT = builder
                .comment("Sky light the dream layer pretends to have, 0 to 15.",
                        "The dream's underground is real terrain with no light sources in it, so it",
                        "renders as black caves. Raising this floors the lightmap of unlit places,",
                        "which lifts caves to a readable ambient level without touching lit surfaces.",
                        "0 keeps vanilla darkness; 15 is full daylight everywhere.")
                .defineInRange("ambientSkyLight", 12, 0, 15);
        SPEC = builder.build();
    }

    private DreamClientConfig() {
    }

    /** {@return whether terrain and water draw with each other's hue wheel} */
    public static boolean swapPalettes() {
        return SWAP_PALETTES.get();
    }

    /** {@return the sky light the dream lightmap never goes below, 0 to 15} */
    public static int ambientSkyLight() {
        return AMBIENT_SKY_LIGHT.get();
    }
}
