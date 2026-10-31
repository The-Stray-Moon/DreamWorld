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

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        SWAP_PALETTES = builder
                .comment("Swap the hue wheels of the terrain and the water.",
                        "Terrain normally uses the pink wheel and water the blue-green one;",
                        "enabling this draws the terrain blue-green and the water pink.")
                .define("swapPalettes", false);
        SPEC = builder.build();
    }

    private DreamClientConfig() {
    }

    /** {@return whether terrain and water draw with each other's hue wheel} */
    public static boolean swapPalettes() {
        return SWAP_PALETTES.get();
    }
}
