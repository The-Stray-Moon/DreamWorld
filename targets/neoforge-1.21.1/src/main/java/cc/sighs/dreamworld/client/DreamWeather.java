package cc.sighs.dreamworld.client;

import cc.sighs.dreamworld.DreamWorldNeoForge121;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Cherry blossoms drifting through the dream layer.
 *
 * <p>The dream layer always has a clear sky (see {@code DreamWeatherMixin}), so rain never shows up
 * in it. Petals fall around the dreamer instead, always but sparsely, and thicker while it rains or
 * storms in reality - so the real weather still reads as a change, just as more blossom rather than
 * as white rain over a pink world.</p>
 *
 * <p>The weather itself is read from the raw level fields, not from the getters: those are pinned to
 * zero for the dream layer by the weather mixin.</p>
 */
@EventBusSubscriber(modid = DreamWorldNeoForge121.MOD_ID, value = Dist.CLIENT)
public final class DreamWeather {
    /** Half width of the square the petals are spawned in, so petals cover a 64x64 area. */
    private static final double SPREAD = 32.0D;
    /** Petals start between this many blocks above the dreamer and twice that. */
    private static final double BASE_HEIGHT = 6.0D;
    /** Light blossom drift while the real weather is clear. */
    private static final int PETALS_PER_TICK_CLEAR = 4;
    /** Thicker blossom while it rains or storms in reality. */
    private static final int PETALS_PER_TICK_STORM = 8;
    /** Weather level above which the real world counts as "not clear". */
    private static final float STORM_THRESHOLD = 0.2F;

    private DreamWeather() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null || minecraft.isPaused()) {
            return;
        }
        if (!DreamClientState.isDreaming()) {
            return;
        }
        int petals = isStormy(level) ? PETALS_PER_TICK_STORM : PETALS_PER_TICK_CLEAR;
        RandomSource random = level.random;
        Vec3 center = minecraft.player.position();
        for (int i = 0; i < petals; i++) {
            double x = center.x + (random.nextDouble() - 0.5D) * SPREAD * 2.0D;
            double z = center.z + (random.nextDouble() - 0.5D) * SPREAD * 2.0D;
            double y = center.y + BASE_HEIGHT + random.nextDouble() * BASE_HEIGHT;
            // The cherry leaf particle drifts on its own; a slow fall keeps it from hanging in place.
            level.addParticle(ParticleTypes.CHERRY_LEAVES, x, y, z, 0.0D, -0.02D, 0.0D);
        }
    }

    /**
     * Whether the real level is rainy or stormy.
     *
     * <p>{@code rainLevel} and {@code thunderLevel} are the values the server syncs to the client,
     * and they are read here before the dream weather override replaces the getters.</p>
     */
    private static boolean isStormy(ClientLevel level) {
        return level.rainLevel > STORM_THRESHOLD || level.thunderLevel > STORM_THRESHOLD;
    }
}
