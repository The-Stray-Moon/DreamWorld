package cc.sighs.dreamworld.client;

import cc.sighs.dreamworld.DreamWorldNeoForge121;
import cc.sighs.dreamworld.boat.TunnelGeometry;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Client-side dream world visuals: pink dream fog and the graffiti tunnels.
 *
 * <p>The pink block tint is not applied here either: it lives in
 * {@code cc.sighs.dreamworld.mixin.client.ModelBlockRendererMixin}, which recolours block faces
 * while the chunk mesh is built, so entities, particles and the HUD keep their normal colours.</p>
 */
@EventBusSubscriber(modid = DreamWorldNeoForge121.MOD_ID, value = Dist.CLIENT)
public final class DreamVisuals {
    /** Hard cap on the number of graffiti pairs that are drawn as tunnels. */
    private static final int MAX_TUNNEL_PAIRS = 64;
    /** Tunnels whose middle point is farther away than this are not drawn. */
    private static final double TUNNEL_VIEW_RANGE = 128.0D;
    /** Half width of a tunnel ribbon in blocks. */
    private static final float TUNNEL_HALF_WIDTH = 0.06F;
    private static final float TUNNEL_RED = 0.55F;
    private static final float TUNNEL_GREEN = 0.8F;
    private static final float TUNNEL_BLUE = 1.0F;
    private static final float TUNNEL_ALPHA = 0.55F;

    private static List<BlockPos> cachedGraffiti;
    private static List<TunnelEdge> cachedTunnels = List.of();

    private DreamVisuals() {
    }

    /** A tunnel segment between two graffiti blocks. */
    public record TunnelEdge(BlockPos from, BlockPos to) {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!DreamClientState.isDreaming() || Minecraft.getInstance().level == null) {
            return;
        }
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            renderTunnels(event.getCamera());
        }
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

    private static List<TunnelEdge> buildTunnels(List<BlockPos> positions) {
        // One source of truth with the boat logic: a boat can only lock onto a drawn tunnel.
        return TunnelGeometry.pairs(positions).stream()
                .map(pair -> new TunnelEdge(pair.from(), pair.to()))
                .toList();
    }

    private static void renderTunnels(Camera camera) {
        List<TunnelEdge> tunnels = tunnels();
        if (tunnels.isEmpty()) {
            return;
        }
        Vec3 eye = camera.getPosition();
        double viewRangeSqr = TUNNEL_VIEW_RANGE * TUNNEL_VIEW_RANGE;
        List<float[]> ribbons = new ArrayList<>(tunnels.size());
        for (TunnelEdge tunnel : tunnels) {
            Vec3 from = Vec3.atCenterOf(tunnel.from());
            Vec3 to = Vec3.atCenterOf(tunnel.to());
            Vec3 middle = from.add(to).scale(0.5D);
            if (eye.distanceToSqr(middle) > viewRangeSqr) {
                continue;
            }
            Vec3 direction = to.subtract(from);
            double lengthSqr = direction.lengthSqr();
            if (lengthSqr < 1.0E-6D) {
                continue;
            }
            direction = direction.scale(1.0D / Math.sqrt(lengthSqr));
            // Billboarding the ribbon towards the camera keeps the tunnel readable from any angle.
            Vec3 side = direction.cross(eye.subtract(middle));
            if (side.lengthSqr() < 1.0E-6D) {
                continue;
            }
            side = side.normalize().scale(TUNNEL_HALF_WIDTH);
            Vec3 a = from.add(side);
            Vec3 b = from.subtract(side);
            Vec3 c = to.subtract(side);
            Vec3 d = to.add(side);
            ribbons.add(new float[] {
                    (float) (a.x - eye.x), (float) (a.y - eye.y), (float) (a.z - eye.z),
                    (float) (b.x - eye.x), (float) (b.y - eye.y), (float) (b.z - eye.z),
                    (float) (c.x - eye.x), (float) (c.y - eye.y), (float) (c.z - eye.z),
                    (float) (d.x - eye.x), (float) (d.y - eye.y), (float) (d.z - eye.z)
            });
        }
        if (ribbons.isEmpty()) {
            return;
        }
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (float[] ribbon : ribbons) {
            buffer.addVertex(ribbon[0], ribbon[1], ribbon[2]).setColor(TUNNEL_RED, TUNNEL_GREEN, TUNNEL_BLUE, TUNNEL_ALPHA);
            buffer.addVertex(ribbon[3], ribbon[4], ribbon[5]).setColor(TUNNEL_RED, TUNNEL_GREEN, TUNNEL_BLUE, TUNNEL_ALPHA);
            buffer.addVertex(ribbon[6], ribbon[7], ribbon[8]).setColor(TUNNEL_RED, TUNNEL_GREEN, TUNNEL_BLUE, TUNNEL_ALPHA);
            buffer.addVertex(ribbon[9], ribbon[10], ribbon[11]).setColor(TUNNEL_RED, TUNNEL_GREEN, TUNNEL_BLUE, TUNNEL_ALPHA);
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
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
