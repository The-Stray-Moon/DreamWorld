package cc.sighs.dreamworld.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side settings of the dream layer.
 *
 * <p>These change how the shared world behaves, so they live in the server config: a dedicated
 * server owns the file and hands the effective values to its clients, which need them to draw the
 * same tunnels the server links.</p>
 */
public final class DreamServerConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.IntValue MAX_TUNNELS_PER_GRAFFITI;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        MAX_TUNNELS_PER_GRAFFITI = builder
                .comment("How many tunnels one graffiti may form.",
                        "A graffiti is always linked to its nearest neighbours, never to arbitrary",
                        "ones, so this is the size of the local network around each block. 1 makes",
                        "every graffiti a dead end that links to a single other one; 5 (the default)",
                        "gives each block up to five connections. The renderer and the boat share the",
                        "value, so raising it adds visible tunnels, never hidden ones.")
                .defineInRange("maxTunnelsPerGraffiti", 5, 1, 64);
        SPEC = builder.build();
    }

    private DreamServerConfig() {
    }

    /** {@return the largest number of tunnels a single graffiti may form} */
    public static int maxTunnelsPerGraffiti() {
        return MAX_TUNNELS_PER_GRAFFITI.get();
    }
}
