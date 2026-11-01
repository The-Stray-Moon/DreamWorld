package cc.sighs.dreamworld.client;

import cc.sighs.dreamworld.DWObjects;
import cc.sighs.dreamworld.net.DreamPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

/**
 * Handlers for the client-bound messages. Only loaded on the physical client.
 */
public final class DWClientPayloadHandler {
    /** Outward speed of the petal burst at radius 0, in blocks per tick. */
    private static final double PETAL_BASE_SPEED = 0.32D;
    /** Extra outward speed per block of blast radius. */
    private static final double PETAL_SPEED_PER_RADIUS = 0.16D;
    /** Upward bias of the burst, so the petals do not all sink immediately. */
    private static final double PETAL_LIFT = 0.06D;

    private DWClientPayloadHandler() {
    }

    public static void handleDreamState(DreamPackets.DreamState payload) {
        boolean previous = DreamClientState.isDreaming();
        if (payload.dreaming()) {
            DreamClientState.setDreaming(true);
            DreamClientPatches.applyAll();
        } else {
            DreamClientState.setDreaming(false);
            DreamClientPatches.restoreAll();
        }
        // The dream tint is baked into block faces, so the chunk meshes must be rebuilt on
        // every transition: pink going in, original colours coming out.
        rebuildBlockMeshes();
        // Both directions get the same blink; only a real change earns one, so the state the
        // server re-sends on login does not blink the screen for nothing.
        if (previous != payload.dreaming()) {
            DreamBlink.trigger();
        }
    }

    /** Valid only on the client thread; the target network adapter runs handlers there. */
    private static void rebuildBlockMeshes() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            minecraft.levelRenderer.allChanged();
        }
    }

    public static void handleGraffitiList(DreamPackets.GraffitiList payload) {
        DreamClientState.setGraffiti(payload.positions(), payload.maxTunnelsPerGraffiti());
    }

    public static void handleDreamEntities(DreamPackets.DreamEntities payload) {
        DreamClientState.setDreamEntityIds(payload.ids());
    }

    public static void handleDreamPatch(DreamPackets.DreamPatch payload) {
        DreamClientPatches.accept(payload.chunkPos(), payload.entries());
    }

    public static void handleAnchorState(DreamPackets.AnchorState payload) {
        DreamClientState.setAnchorBound(payload.bound());
    }

    public static void handleDreamers(DreamPackets.Dreamers payload) {
        DreamClientState.setDreamers(payload.dreamers());
    }

    /**
     * Spawns the wand's petal burst.
     *
     * <p>Each petal starts slightly off the centre and moves outwards along that same direction, so
     * the whole cloud reads as an explosion rather than a drifting puff. A little upward bias keeps
     * the petals from all dropping into the ground right away.</p>
     */
    public static void handleBlastParticles(DreamPackets.BlastParticles payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        RandomSource random = level.random;
        // Larger blasts throw the petals further out.
        double speed = PETAL_BASE_SPEED + payload.radius() * PETAL_SPEED_PER_RADIUS;
        for (int index = 0; index < payload.count(); index++) {
            double theta = random.nextDouble() * Mth.TWO_PI;
            double cosPhi = random.nextDouble() * 2.0D - 1.0D;
            double sinPhi = Math.sqrt(1.0D - cosPhi * cosPhi);
            double dx = sinPhi * Math.cos(theta);
            double dy = cosPhi;
            double dz = sinPhi * Math.sin(theta);
            double burst = speed * (0.5D + 0.5D * random.nextDouble());
            double offset = payload.radius() * 0.15D * random.nextDouble();
            level.addParticle(DWObjects.DREAM_PETAL.get(),
                    payload.x() + dx * offset,
                    payload.y() + dy * offset,
                    payload.z() + dz * offset,
                    dx * burst,
                    dy * burst + PETAL_LIFT,
                    dz * burst);
        }
    }

    /** Rebuilds the transferred creature's model as a petal silhouette where it stood. */
    public static void handlePetalShape(DreamPackets.PetalShape payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        EntityType<?> type = net.minecraft.core.Registry.ENTITY_TYPE.get(payload.entityType());
        PetalSilhouette.spawn(level, type,
                new Vec3(payload.x(), payload.y(), payload.z()), payload.yRot(), payload.count());
    }
}
