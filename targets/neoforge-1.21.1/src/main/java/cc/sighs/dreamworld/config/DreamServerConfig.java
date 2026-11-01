package cc.sighs.dreamworld.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side settings of the dream layer.
 *
 * <p>These change how the shared world behaves, so they live in the server config: a dedicated
 * server owns the file. Tunnel settings are also sent to the clients so they draw the same network
 * the server links; sleep settings stay on the server, where sleeping is resolved.</p>
 */
public final class DreamServerConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.IntValue MAX_TUNNELS_PER_GRAFFITI;
    private static final ModConfigSpec.IntValue DREAM_ENTRY_DELAY_TICKS;
    private static final ModConfigSpec.DoubleValue GRAFFITI_SLEEP_RANGE;
    private static final ModConfigSpec.DoubleValue MAX_GRAFFITI_SLEEP_SPEEDUP;

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
        DREAM_ENTRY_DELAY_TICKS = builder
                .comment("Ticks a player with the dream walk effect lies still before entering the dream.",
                        "20 ticks = 1 second; the default of 60 is 3 seconds.",
                        "Kept below 100 ticks so the wait cannot turn into a vanilla 'everybody slept'",
                        "night skip before the dream starts.")
                .defineInRange("dreamEntryDelayTicks", 60, 1, 99);
        GRAFFITI_SLEEP_RANGE = builder
                .comment("Distance in blocks within which dream graffiti shorten the sleep that",
                        "enters or leaves the dream. 0 disables the speed-up.")
                .defineInRange("graffitiSleepRange", 16.0D, 0.0D, 128.0D);
        MAX_GRAFFITI_SLEEP_SPEEDUP = builder
                .comment("Largest share of that sleep delay graffiti may remove at zero distance.",
                        "0.6 removes 60% of the delay; 1.0 leaves only the one-tick minimum.")
                .defineInRange("maxGraffitiSleepSpeedup", 0.6D, 0.0D, 1.0D);
        SPEC = builder.build();
    }

    private DreamServerConfig() {
    }

    /** {@return the largest number of tunnels a single graffiti may form} */
    public static int maxTunnelsPerGraffiti() {
        return MAX_TUNNELS_PER_GRAFFITI.get();
    }

    /** {@return ticks a dream walker lies still before entering the dream} */
    public static int dreamEntryDelayTicks() {
        return DREAM_ENTRY_DELAY_TICKS.get();
    }

    /** {@return distance in blocks at which dream graffiti begin to shorten the dream sleep} */
    public static double graffitiSleepRange() {
        return GRAFFITI_SLEEP_RANGE.get();
    }

    /** {@return largest share of a dream sleep delay graffiti may remove, at zero distance} */
    public static double maxGraffitiSleepSpeedup() {
        return MAX_GRAFFITI_SLEEP_SPEEDUP.get();
    }
}
