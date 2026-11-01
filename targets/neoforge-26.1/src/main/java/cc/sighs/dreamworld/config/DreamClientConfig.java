package cc.sighs.dreamworld.config;

import cc.sighs.dreamworld.spi.DreamColorMode;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-side settings of the dream layer.
 *
 * <p>Everything here only affects how the dream is drawn, so it lives in a client config: a
 * dedicated server never reads it and the file stays per-installation.</p>
 */
public final class DreamClientConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.EnumValue<DreamColorMode> COLOR_MODE;
    private static final ModConfigSpec.IntValue AMBIENT_SKY_LIGHT;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        COLOR_MODE = builder
                .comment("How the dream layer colours terrain and water.",
                        "MODE_1: terrain pink, water blue-green (the original look).",
                        "MODE_2: terrain blue-green, water pink.",
                        "RANDOM: pick MODE_1 or MODE_2 each time the client enters the dream.")
                .defineEnum("colorMode", DreamColorMode.RANDOM);
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

    /** {@return how terrain and water are coloured} */
    public static DreamColorMode colorMode() {
        return COLOR_MODE.get();
    }

    /** {@return the sky light the dream lightmap never goes below, 0 to 15} */
    public static int ambientSkyLight() {
        return AMBIENT_SKY_LIGHT.get();
    }
}
