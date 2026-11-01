package cc.sighs.dreamworld.client;

import cc.sighs.dreamworld.DWObjects;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Leaves a cherry-petal silhouette of a creature where it stood when an intrusion took it away.
 *
 * <p>The shape comes from the creature's own model, which only exists on the client. A temporary
 * entity of the same type is posed at the spot and rendered into a buffer that records every vertex,
 * so the petals sit on the actual surface of the model instead of on a featureless box. If the model
 * cannot be rebuilt for any reason, the bounding box is filled in as a fallback.</p>
 *
 * <p>1.18.2's {@link VertexConsumer} has the legacy {@code vertex/color/uv/uv2/normal/endVertex}
 * shape, which is what the collector implements.</p>
 */
public final class PetalSilhouette {
    /** Petals placed at one vertex, plus a little spread so the cloud has some body. */
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
    public static void spawn(ClientLevel level, EntityType<?> type, Vec3 origin, float yRot,
            int count) {
        List<Vec3> surface;
        try {
            surface = captureSurface(level, type, origin, yRot);
        } catch (RuntimeException exception) {
            // A renderer that cannot handle a detached entity must not break the intrusion visuals.
            surface = List.of();
        }
        if (surface.isEmpty()) {
            surface = fillBoundingBox(type, origin, count);
        }
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

    /** Renders a posed copy of the creature and returns the vertices of its model. */
    private static List<Vec3> captureSurface(ClientLevel level, EntityType<?> type, Vec3 origin,
            float yRot) {
        Entity ghost = type.create(level);
        if (ghost == null) {
            return List.of();
        }
        ghost.moveTo(origin.x, origin.y, origin.z, yRot, 0.0F);
        @SuppressWarnings("unchecked")
        EntityRenderer<Entity> renderer = (EntityRenderer<Entity>)
                Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(ghost);
        VertexCollector collector = new VertexCollector();
        // The renderer expects the pose stack to be positioned at the entity already, so an identity
        // stack yields vertices relative to the creature's own origin.
        renderer.render(ghost, yRot, 1.0F, new PoseStack(), collector, LightTexture.FULL_BRIGHT);
        return collector.points();
    }

    /** Fallback shape: the creature's collision box. */
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
        return (level.random.nextDouble() - 0.5D) * JITTER * 2.0D;
    }

    /** Records every vertex the renderer emits, in entity-local space. */
    private static final class VertexCollector implements MultiBufferSource, VertexConsumer {
        private final List<Vec3> points = new ArrayList<>();

        List<Vec3> points() {
            return this.points;
        }

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            return this;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            this.points.add(new Vec3(x, y, z));
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public void endVertex() {
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
        }

        @Override
        public void unsetDefaultColor() {
        }
    }
}
