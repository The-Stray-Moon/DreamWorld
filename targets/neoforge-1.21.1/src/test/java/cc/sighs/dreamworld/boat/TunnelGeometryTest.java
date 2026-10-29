package cc.sighs.dreamworld.boat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class TunnelGeometryTest {
    private static final double EPSILON = 1.0E-6D;

    @Test
    void needsTwoAnchors() {
        assertNull(TunnelGeometry.nearest(List.of(), Vec3.ZERO));
        assertNull(TunnelGeometry.nearest(List.of(new BlockPos(0, 0, 0)), Vec3.ZERO));
    }

    @Test
    void findsTheSegmentUnderTheBoat() {
        List<BlockPos> anchors = List.of(new BlockPos(0, 0, -2), new BlockPos(0, 0, 2));
        TunnelGeometry.Tunnel tunnel = TunnelGeometry.nearest(anchors, new Vec3(0.5D, 0.5D, 0.5D));
        assertNotNull(tunnel);
        assertEquals(0.0D, tunnel.distanceSq(), EPSILON);
        assertEquals(0.5D, tunnel.t(), EPSILON);
        assertEquals(0.5D, tunnel.projection().y, EPSILON);
        assertEquals(4.0D, tunnel.length(), EPSILON);
        assertEquals(2.0D, tunnel.remaining(1.0D), EPSILON);
        assertEquals(2.0D, tunnel.remaining(-1.0D), EPSILON);
    }

    @Test
    void measuresTheDistanceToTheClosestOfSeveralTunnels() {
        List<BlockPos> anchors = List.of(
                new BlockPos(0, 0, -20), new BlockPos(0, 0, -10),
                new BlockPos(0, 0, 10), new BlockPos(0, 0, 20));
        TunnelGeometry.Tunnel tunnel = TunnelGeometry.nearest(anchors, new Vec3(0.5D, 3.5D, -15.0D));
        assertNotNull(tunnel);
        // The point sits above the middle of the near segment.
        assertEquals(9.0D, tunnel.distanceSq(), 1.0E-4D);
    }

    @Test
    void keepsPairCountBounded() {
        List<BlockPos> anchors = new ArrayList<>();
        for (int i = 0; i < 70; i++) {
            anchors.add(new BlockPos(i * 2, 0, 0));
        }
        TunnelGeometry.Tunnel tunnel = TunnelGeometry.nearest(anchors, new Vec3(3.0D, 0.0D, 0.0D));
        assertNotNull(tunnel);
        assertTrue(tunnel.distanceSq() >= 0.0D);
        // Linked to its neighbour, so the point must land on the segment between 2 and 4.
        assertEquals(3.5D, tunnel.projection().x, 1.5D);
    }

    @Test
    void pairsEveryCombinationWhenFew() {
        List<BlockPos> anchors = List.of(new BlockPos(0, 0, 0), new BlockPos(4, 0, 0), new BlockPos(0, 0, 4));
        assertEquals(3, TunnelGeometry.pairs(anchors).size());
        assertEquals(0, TunnelGeometry.pairs(List.of(new BlockPos(0, 0, 0))).size());
    }

    @Test
    void pairsStayBoundedForManyGraffiti() {
        List<BlockPos> anchors = new ArrayList<>();
        for (int i = 0; i < 70; i++) {
            anchors.add(new BlockPos(i * 2, 0, 0));
        }
        List<TunnelGeometry.Pair> pairs = TunnelGeometry.pairs(anchors);
        assertTrue(pairs.size() <= TunnelGeometry.MAX_PAIRS);
        assertTrue(pairs.size() >= 2);
    }

    @Test
    void directionIsNormalised() {
        List<BlockPos> anchors = List.of(new BlockPos(0, 0, 0), new BlockPos(6, 0, 0));
        TunnelGeometry.Tunnel tunnel = TunnelGeometry.nearest(anchors, new Vec3(3.0D, 0.0D, 0.0D));
        assertNotNull(tunnel);
        Vec3 direction = tunnel.direction();
        assertEquals(1.0D, direction.length(), EPSILON);
        assertEquals(1.0D, direction.x, EPSILON);
    }
}
