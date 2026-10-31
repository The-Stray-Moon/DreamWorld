package cc.sighs.dreamworld.client;

import cc.sighs.dreamworld.DreamWorldNeoForge121;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Starry wake behind a boat driven through the dream layer.
 *
 * <p>A dream boat may slide over any block at blue-ice speed, so its wake is the only cue of how fast
 * it is going. The trail is made of the glow squid's own sparkle particle - the small green twinkling
 * stars that hover around the squid - which fits the pale, lit dream palette. It is emitted behind the
 * hull, a faster boat meaning a denser trail.</p>
 *
 * <p>Client-only and purely decorative: nothing is synced, other players' clients draw their own.
 * Only the boat the local player is driving is considered, because that is the one the dreamer can
 * actually see themselves move.</p>
 */
@EventBusSubscriber(modid = DreamWorldNeoForge121.MOD_ID, value = Dist.CLIENT)
public final class DreamBoatTrail {
    /** Horizontal speed below which the boat is considered parked and leaves no wake. */
    private static final double MIN_SPEED_SQR = 0.004D;
    /** Distance behind the hull centre the sparks are released at. */
    private static final double TRAIL_OFFSET = 0.7D;
    /** Hard cap per tick, so a tunnel-speed run cannot flood the particle engine. */
    private static final int MAX_PARTICLES_PER_TICK = 6;

    private DreamBoatTrail() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || minecraft.isPaused()) {
            return;
        }
        if (!DreamClientState.isDreaming() || !(player.getVehicle() instanceof Boat boat)) {
            return;
        }
        Vec3 motion = boat.getDeltaMovement();
        double speedSqr = motion.x * motion.x + motion.z * motion.z;
        if (speedSqr < MIN_SPEED_SQR) {
            return;
        }
        Vec3 direction = new Vec3(motion.x, 0.0D, motion.z).normalize();
        Vec3 centre = boat.position().add(direction.scale(-TRAIL_OFFSET)).add(0.0D, 0.1D, 0.0D);
        Vec3 side = new Vec3(-direction.z, 0.0D, direction.x);
        // Wider boats trail a little across the hull, so the wake does not read as a single thread.
        double halfWidth = boat.getBbWidth() * 0.35D;
        int count = Mth.clamp((int) Math.ceil(speedSqr * 30.0D), 1, MAX_PARTICLES_PER_TICK);
        for (int index = 0; index < count; index++) {
            double spread = (level.random.nextDouble() - 0.5D) * 2.0D * halfWidth;
            Vec3 point = centre.add(side.scale(spread));
            // The glow sparkle drifts on its own; a faint backward nudge keeps the trail behind the
            // boat instead of hanging over it.
            level.addParticle(ParticleTypes.GLOW, point.x, point.y, point.z,
                    -motion.x * 0.05D, 0.01D, -motion.z * 0.05D);
        }
    }
}
