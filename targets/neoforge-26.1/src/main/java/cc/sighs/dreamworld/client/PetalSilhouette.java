package cc.sighs.dreamworld.client;

import cc.sighs.dreamworld.DWObjects;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Leaves a cherry-petal silhouette of a creature where it stood when an intrusion took it away.
 *
 * <p>In the 1.21 family the shape came from the creature's own model: a temporary entity was posed
 * and rendered into a vertex-recording buffer. 26.1 only renders prepared states through a
 * {@code SubmitNodeCollector}, which cannot be fed a stand-in buffer, so the silhouette is built from
 * the creature's collision box lattice instead. Every captured point still becomes one dream petal
 * with a small spread, which keeps the visual of an intruded creature falling apart.</p>
 */
public final class PetalSilhouette {
    /** Petals placed at one point, plus a little spread so the cloud has some body. */
    private static final double JITTER = 0.035D;
    /** Slow fall of the settled petals, so they settle into a pool instead of hanging in the air. */
    private static final double FALL = 0.01D;

    private PetalSilhouette() {
    }

    /**
     * Spawns the petals for one transferred creature.
     *
     * @param count how many petals to place along the captured surface
     */
    public static void spawn(ClientLevel level, EntityType<?> type, Vec3 origin, float yRot, int count) {
        List<Vec3> surface = type == null ? List.of() : fillBoundingBox(type, origin, count);
        if (surface.isEmpty()) {
            return;
        }
        // Walk the captured surface evenly, so a dense model does not only light up one limb.
        int step = Math.max(1, surface.size() / Math.max(1, count));
        for (int index = 0; index < surface.size(); index += step) {
            Vec3 point = surface.get(index);
            level.addParticle(DWObjects.DREAM_PETAL.get(),
                    origin.x + point.x + jitter(level),
                    origin.y + point.y + jitter(level),
                    origin.z + point.z + jitter(level),
                    jitter(level) * 2.0D,
                    -FALL,
                    jitter(level) * 2.0D);
        }
    }

    /** Shape: the creature's collision box, sampled on a fixed lattice. */
    private static List<Vec3> fillBoundingBox(EntityType<?> type, Vec3 origin, int count) {
        AABB box = type.getDimensions().makeBoundingBox(Vec3.ZERO);
        List<Vec3> points = new ArrayList<>(count);
        // A fixed lattice keeps the fallback stable between frames instead of flickering randomly.
        int side = Math.max(2, (int) Math.cbrt(count));
        for (int x = 0; x < side; x++) {
            for (int y = 0; y < side; y++) {
                for (int z = 0; z < side; z++) {
                    points.add(new Vec3(
                            box.minX + (box.maxX - box.minX) * (x + 0.5D) / side,
                            box.minY + (box.maxY - box.minY) * (y + 0.5D) / side,
                            box.minZ + (box.maxZ - box.minZ) * (z + 0.5D) / side));
                }
            }
        }
        return points;
    }

    private static double jitter(ClientLevel level) {
        return (level.getRandom().nextDouble() - 0.5D) * JITTER * 2.0D;
    }
}
