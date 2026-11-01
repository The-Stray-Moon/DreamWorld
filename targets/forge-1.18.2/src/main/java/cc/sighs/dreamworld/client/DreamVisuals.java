package cc.sighs.dreamworld.client;

import cc.sighs.dreamworld.boat.DreamTunnelBoat;
import cc.sighs.dreamworld.boat.TunnelGeometry;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side dream world visuals: pink dream fog and the graffiti tunnels.
 *
 * <p>The pink block tint is not applied here either: it lives in
 * {@code cc.sighs.dreamworld.mixin.client.ModelBlockRendererMixin}, which recolours block faces
 * while the chunk mesh is built, so entities, particles and the HUD keep their normal colours.</p>
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

    private static List<BlockPos> cachedGraffiti;
    private static List<TunnelEdge> cachedTunnels = List.of();

    private DreamVisuals() {
    }

    /** A tunnel segment between two graffiti blocks. */
    public record TunnelEdge(BlockPos from, BlockPos to) {
    }

    public static void render(Camera camera, PoseStack poseStack) {
        if (!DreamClientState.isDreaming() || Minecraft.getInstance().level == null) {
            return;
        }
        renderTunnels(camera, poseStack);
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
            if (distanceToSegmentSqr(position, Vec3.atCenterOf(tunnel.from()),
                    Vec3.atCenterOf(tunnel.to())) <= rangeSqr) {
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

    private static void renderTunnels(Camera camera, PoseStack poseStack) {
        List<TunnelEdge> tunnels = tunnels();
        if (tunnels.isEmpty()) {
            return;
        }
        Vec3 eye = camera.getPosition();
        // The ribbon is a screen-space billboard: one side direction for the whole pass, taken from
        // the camera. The previous per-segment cross product collapsed to zero whenever the camera
        // sat on the line - which is exactly where a rider is - so the tunnel being ridden was the
        // one that disappeared.
        Vec3 left = new Vec3(camera.getLeftVector());
        AimedTunnel aimed = aimedTunnel(eye, new Vec3(camera.getLookVector()), AIM_REACH, AIM_RADIUS);
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
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        // Tunnels run straight through terrain, so they have to be readable through it as well.
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        // 1.18.2 does not leave the camera rotation on the RenderSystem model-view stack at this
        // stage, unlike 1.21.1's LevelRenderer. The ribbon vertices are camera-relative, so without
        // applying the event's pose the whole tunnel stays in view space and flies off as the camera
        // turns. Push and restore it so the clouds and weather that follow keep the vanilla matrix.
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        modelView.mulPoseMatrix(poseStack.last().pose());
        RenderSystem.applyModelViewMatrix();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        // Plain first: with the depth test off, the highlighted ribbons land on top of them.
        plain.forEach(ribbon -> dreamworld$ribbon(buffer, ribbon));
        highlighted.forEach(ribbon -> dreamworld$ribbon(buffer, ribbon));
        tesselator.end();
        modelView.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /** Writes one ribbon; the last four floats are its colour. */
    private static void dreamworld$ribbon(BufferBuilder buffer, float[] ribbon) {
        for (int vertex = 0; vertex < 4; vertex++) {
            int base = vertex * 3;
            buffer.vertex(ribbon[base], ribbon[base + 1], ribbon[base + 2])
                    .color(ribbon[12], ribbon[13], ribbon[14], ribbon[15])
                    .endVertex();
        }
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
        Player player = Minecraft.getInstance().player;
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

    private record ScoredEdge(TunnelEdge edge, double lengthSqr) {
    }
}
