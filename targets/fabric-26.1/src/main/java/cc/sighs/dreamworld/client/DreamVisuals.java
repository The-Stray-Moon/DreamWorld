package cc.sighs.dreamworld.client;

import cc.sighs.dreamworld.boat.DreamTunnelBoat;
import cc.sighs.dreamworld.boat.TunnelGeometry;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side dream world visuals: the graffiti tunnels.
 *
 * <p>The pink block tint is not applied here either: it lives in
 * {@code cc.sighs.dreamworld.mixin.client.ModelBlockRendererMixin}, which recolours block faces
 * while the chunk mesh is built, so entities, particles and the HUD keep their normal colours.</p>
 *
 * <p>26.1 has no immediate-mode {@code RenderSystem} path left in the level renderer: geometry is
 * submitted to the frame's {@link SubmitNodeCollector}. The ribbons therefore go through a dedicated
 * pipeline that keeps the depth test disabled, because a tunnel runs straight through terrain and
 * has to stay readable through it.</p>
 */
public final class DreamVisuals {
    /** A tunnel whose segment comes closer than this to the camera is drawn. */
    private static final double TUNNEL_VIEW_RANGE = 128.0D;
    /** Half width of a tunnel ribbon in blocks. */
    private static final float TUNNEL_HALF_WIDTH = 0.25F;
    private static final float TUNNEL_RED = 0.55F;
    private static final float TUNNEL_GREEN = 0.8F;
    private static final float TUNNEL_BLUE = 1.0F;
    private static final float TUNNEL_ALPHA = 0.55F;

    /** The tunnel under the crosshair and the one being ridden are drawn fatter and brighter. */
    private static final float HIGHLIGHT_HALF_WIDTH = 0.45F;
    private static final float HIGHLIGHT_RED = 0.92F;
    private static final float HIGHLIGHT_GREEN = 0.97F;
    private static final float HIGHLIGHT_BLUE = 1.0F;
    private static final float HIGHLIGHT_ALPHA = 0.9F;

    /** How far the crosshair reaches when looking for a tunnel to point at. */
    public static final double AIM_REACH = 32.0D;
    /** How far off the line the crosshair may be and still count as pointing at a tunnel. */
    public static final double AIM_RADIUS = 0.75D;

    /** Geometry-only pipeline: position+colour quads, translucent, no depth test, no culling. */
    public static final RenderPipeline TUNNEL_PIPELINE = RenderPipeline
            .builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation("pipeline/dream_tunnel")
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .build();

    private static RenderType tunnelRenderType;

    private static List<BlockPos> cachedGraffiti;
    private static List<TunnelEdge> cachedTunnels = List.of();

    private DreamVisuals() {
    }

    /** A tunnel segment between two graffiti blocks. */
    public record TunnelEdge(BlockPos from, BlockPos to) {
    }

    /** Builds the render type once the pipeline has been registered. */
    public static RenderType tunnelRenderType() {
        if (tunnelRenderType == null) {
            tunnelRenderType = RenderType.create("dream_tunnel",
                    RenderSetup.builder(TUNNEL_PIPELINE).sortOnUpload().createRenderSetup());
        }
        return tunnelRenderType;
    }

    /**
     * Submits the tunnels of the dream layer. Called from the custom geometry event, where the pose
     * stack is already camera relative.
     */
    public static void render(LevelRenderState levelState, PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector) {
        if (!DreamClientState.isDreaming() || Minecraft.getInstance().level == null) {
            return;
        }
        List<TunnelEdge> tunnels = tunnels();
        if (tunnels.isEmpty()) {
            return;
        }
        CameraRenderState camera = levelState.cameraRenderState;
        Vec3 eye = camera.pos;
        // The ribbon is a screen-space billboard: one side direction for the whole pass, taken from
        // the camera. A per-segment cross product collapsed to zero whenever the camera sat on the
        // line - which is exactly where a rider is - so the tunnel being ridden was the one that
        // disappeared.
        Vec3 look = Vec3.directionFromRotation(camera.xRot, camera.yRot);
        Vec3 left = new Vec3(0.0D, 1.0D, 0.0D).cross(look).normalize();
        AimedTunnel aimed = aimedTunnel(eye, look, AIM_REACH, AIM_RADIUS);
        TunnelGeometry.Pair ridden = riddenTunnel();
        double viewRangeSqr = TUNNEL_VIEW_RANGE * TUNNEL_VIEW_RANGE;
        List<float[]> plain = new ArrayList<>(tunnels.size());
        List<float[]> highlighted = new ArrayList<>(2);
        for (TunnelEdge tunnel : tunnels) {
            Vec3 from = Vec3.atCenterOf(tunnel.from());
            Vec3 to = Vec3.atCenterOf(tunnel.to());
            // Cull by the distance to the whole segment: a long tunnel can have its middle far away
            // while one of its mouths is right in front of the player.
            if (distanceToSegmentSqr(eye, from, to) > viewRangeSqr) {
                continue;
            }
            boolean highlight = matches(tunnel, aimed) || matches(tunnel, ridden);
            Vec3 side = left.scale(highlight ? HIGHLIGHT_HALF_WIDTH : TUNNEL_HALF_WIDTH);
            Vec3 a = from.add(side);
            Vec3 b = from.subtract(side);
            Vec3 c = to.subtract(side);
            Vec3 d = to.add(side);
            float[] color = highlight
                    ? new float[]{HIGHLIGHT_RED, HIGHLIGHT_GREEN, HIGHLIGHT_BLUE, HIGHLIGHT_ALPHA}
                    : new float[]{TUNNEL_RED, TUNNEL_GREEN, TUNNEL_BLUE, TUNNEL_ALPHA};
            float[] ribbon = {
                    (float) (a.x - eye.x), (float) (a.y - eye.y), (float) (a.z - eye.z),
                    (float) (b.x - eye.x), (float) (b.y - eye.y), (float) (b.z - eye.z),
                    (float) (c.x - eye.x), (float) (c.y - eye.y), (float) (c.z - eye.z),
                    (float) (d.x - eye.x), (float) (d.y - eye.y), (float) (d.z - eye.z),
                    color[0], color[1], color[2], color[3]
            };
            (highlight ? highlighted : plain).add(ribbon);
        }
        if (plain.isEmpty() && highlighted.isEmpty()) {
            return;
        }
        RenderType type = tunnelRenderType();
        // Plain first: without the depth test the highlighted ribbons land on top of them.
        submitRibbons(submitNodeCollector, poseStack, type, plain);
        submitRibbons(submitNodeCollector, poseStack, type, highlighted);
    }

    private static void submitRibbons(SubmitNodeCollector collector, PoseStack poseStack,
            RenderType type, List<float[]> ribbons) {
        if (ribbons.isEmpty()) {
            return;
        }
        collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> {
            for (float[] ribbon : ribbons) {
                for (int vertex = 0; vertex < 4; vertex++) {
                    int base = vertex * 3;
                    buffer.addVertex(pose, ribbon[base], ribbon[base + 1], ribbon[base + 2])
                            .setColor(ribbon[12], ribbon[13], ribbon[14], ribbon[15]);
                }
            }
        });
    }

    /**
     * Tunnels currently known to the client. The list is rebuilt only when the synced graffiti list
     * changes, so it is safe to call every frame.
     */
    public static List<TunnelEdge> tunnels() {
        List<BlockPos> source = DreamClientState.graffiti();
        if (source != cachedGraffiti) {
            cachedTunnels = buildTunnels(source);
            cachedGraffiti = source;
        }
        return cachedTunnels;
    }

    /** Whether {@code position} lies within {@code range} blocks of any tunnel segment. */
    public static boolean isNearTunnel(Vec3 position, double range) {
        double rangeSqr = range * range;
        for (TunnelEdge tunnel : tunnels()) {
            if (distanceToSegmentSqr(position, Vec3.atCenterOf(tunnel.from()), Vec3.atCenterOf(tunnel.to())) <= rangeSqr) {
                return true;
            }
        }
        return false;
    }

    /** The tunnel the crosshair points at, or {@code null} when none is within reach. */
    public static AimedTunnel aimedTunnel(Vec3 origin, Vec3 direction, double reach, double radius) {
        double radiusSqr = radius * radius;
        AimedTunnel best = null;
        for (TunnelEdge tunnel : tunnels()) {
            Vec3 from = Vec3.atCenterOf(tunnel.from());
            Vec3 to = Vec3.atCenterOf(tunnel.to());
            TunnelGeometry.Aim aim = TunnelGeometry.aimAt(origin, direction, from, to);
            if (aim.along() < 0.0D || aim.along() > reach || aim.distanceSq() > radiusSqr) {
                continue;
            }
            if (best == null || aim.distanceSq() < best.distanceSq()) {
                best = new AimedTunnel(tunnel, aim.along(), aim.distanceSq());
            }
        }
        return best;
    }

    /** A tunnel under the crosshair, with where along the ray and how close it was hit. */
    public record AimedTunnel(TunnelEdge tunnel, double along, double distanceSq) {
    }

    private static List<TunnelEdge> buildTunnels(List<BlockPos> positions) {
        // One source of truth with the boat logic: a boat can only attach to a drawn tunnel.
        return TunnelGeometry.pairs(positions, DreamClientState.maxTunnelsPerGraffiti()).stream()
                .map(pair -> new TunnelEdge(pair.from(), pair.to()))
                .toList();
    }

    /** {@return whether {@code tunnel} is the segment under the crosshair} */
    private static boolean matches(TunnelEdge tunnel, @Nullable AimedTunnel aimed) {
        return aimed != null && sameEdge(tunnel, aimed.tunnel());
    }

    /** {@return whether {@code tunnel} is the line the local player is riding} */
    private static boolean matches(TunnelEdge tunnel, @Nullable TunnelGeometry.Pair ridden) {
        if (ridden == null) {
            return false;
        }
        return tunnel.from().equals(ridden.from()) && tunnel.to().equals(ridden.to())
                || tunnel.from().equals(ridden.to()) && tunnel.to().equals(ridden.from());
    }

    private static boolean sameEdge(TunnelEdge first, TunnelEdge second) {
        return first.from().equals(second.from()) && first.to().equals(second.to())
                || first.from().equals(second.to()) && first.to().equals(second.from());
    }

    /** {@return the pair of the tunnel the local player rides, or {@code null}} */
    @Nullable
    private static TunnelGeometry.Pair riddenTunnel() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player.getVehicle() instanceof DreamTunnelBoat tunnelBoat) {
            return tunnelBoat.currentDreamTunnel();
        }
        return null;
    }

    private static double distanceToSegmentSqr(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 segment = end.subtract(start);
        double lengthSqr = segment.lengthSqr();
        if (lengthSqr < 1.0E-6D) {
            return point.distanceToSqr(start);
        }
        double t = Mth.clamp(point.subtract(start).dot(segment) / lengthSqr, 0.0D, 1.0D);
        return point.distanceToSqr(start.add(segment.scale(t)));
    }
}
