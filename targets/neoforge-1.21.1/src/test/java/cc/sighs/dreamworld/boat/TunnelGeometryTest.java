package cc.sighs.dreamworld.boat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    private static final int LIMIT = 5;

    @Test
    void needsTwoAnchors() {
        assertNull(TunnelGeometry.nearest(List.of(), Vec3.ZERO, LIMIT));
        assertNull(TunnelGeometry.nearest(List.of(new BlockPos(0, 0, 0)), Vec3.ZERO, LIMIT));
    }

    @Test
    void findsTheSegmentUnderTheBoat() {
        List<BlockPos> anchors = List.of(new BlockPos(0, 0, -2), new BlockPos(0, 0, 2));
        TunnelGeometry.Tunnel tunnel = TunnelGeometry.nearest(anchors, new Vec3(0.5D, 0.5D, 0.5D), LIMIT);
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
        TunnelGeometry.Tunnel tunnel = TunnelGeometry.nearest(anchors, new Vec3(0.5D, 3.5D, -15.0D), LIMIT);
        assertNotNull(tunnel);
        // The point sits above the middle of the near segment.
        assertEquals(9.0D, tunnel.distanceSq(), 1.0E-4D);
    }

    @Test
    void linksEachGraffitiToItsNeighbours() {
        List<BlockPos> anchors = new ArrayList<>();
        for (int i = 0; i < 70; i++) {
            anchors.add(new BlockPos(i * 2, 0, 0));
        }
        TunnelGeometry.Tunnel tunnel = TunnelGeometry.nearest(anchors, new Vec3(3.0D, 0.0D, 0.0D), LIMIT);
        assertNotNull(tunnel);
        assertTrue(tunnel.distanceSq() >= 0.0D);
        // Linked to its neighbour, so the point must land on the segment between 2 and 4.
        assertEquals(3.5D, tunnel.projection().x, 1.5D);
    }

    @Test
    void pairsEveryCombinationWhenFewAndTheLimitAllowsIt() {
        List<BlockPos> anchors = List.of(new BlockPos(0, 0, 0), new BlockPos(4, 0, 0), new BlockPos(0, 0, 4));
        // Three blocks with a limit of five: everyone links to everyone, one edge per pair.
        assertEquals(3, TunnelGeometry.pairs(anchors, LIMIT).size());
        assertEquals(0, TunnelGeometry.pairs(List.of(new BlockPos(0, 0, 0)), LIMIT).size());
    }

    @Test
    void everyGraffitiStaysWithinTheLimit() {
        List<BlockPos> anchors = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            anchors.add(new BlockPos(i * 2, 0, 0));
        }
        List<TunnelGeometry.Pair> pairs = TunnelGeometry.pairs(anchors, LIMIT);
        assertFalse(pairs.isEmpty());
        for (BlockPos anchor : anchors) {
            long linked = pairs.stream()
                    .filter(pair -> pair.from().equals(anchor) || pair.to().equals(anchor))
                    .count();
            assertTrue(linked <= LIMIT, "a graffiti formed " + linked + " tunnels");
        }
    }

    @Test
    void aLimitOfOneLeavesEveryGraffitiWithASingleTunnel() {
        List<BlockPos> anchors = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            anchors.add(new BlockPos(i * 2, 0, 0));
        }
        List<TunnelGeometry.Pair> pairs = TunnelGeometry.pairs(anchors, 1);
        for (BlockPos anchor : anchors) {
            long linked = pairs.stream()
                    .filter(pair -> pair.from().equals(anchor) || pair.to().equals(anchor))
                    .count();
            assertTrue(linked <= 1, "a graffiti formed " + linked + " tunnels");
        }
    }

    @Test
    void forPairReturnsThatTunnelEvenWhenItIsNotTheClosest() {
        BlockPos a = new BlockPos(0, 0, 0);
        BlockPos b = new BlockPos(0, 0, 10);
        BlockPos c = new BlockPos(10, 0, 0);
        List<BlockPos> anchors = List.of(a, b, c);
        TunnelGeometry.Tunnel tunnel = TunnelGeometry.forPair(anchors,
                new TunnelGeometry.Pair(a, b), new Vec3(5.0D, 0.0D, 0.0D), LIMIT);
        assertNotNull(tunnel);
        // The point sits on the a-c line, but the requested tunnel is a-b: 4.5 across, 0.5 up and
        // 0.5 back to the block centre.
        assertEquals(20.75D, tunnel.distanceSq(), 1.0E-4D);
    }

    @Test
    void nearestCanSkipTheLineBeingRidden() {
        BlockPos a = new BlockPos(0, 0, 0);
        BlockPos b = new BlockPos(0, 0, 10);
        BlockPos c = new BlockPos(10, 0, 0);
        List<BlockPos> anchors = List.of(a, b, c);
        Vec3 point = new Vec3(5.0D, 0.0D, 0.0D);
        TunnelGeometry.Tunnel onLine = TunnelGeometry.nearest(anchors, point, LIMIT,
                new TunnelGeometry.Pair(a, b));
        assertNotNull(onLine);
        assertFalse(TunnelGeometry.samePair(onLine.pair(), new TunnelGeometry.Pair(a, b)));
        // The point lies on the a-c line, half a block below and beside its centre.
        assertEquals(0.5D, onLine.distanceSq(), 1.0E-4D);
    }

    @Test
    void directionIsNormalised() {
        List<BlockPos> anchors = List.of(new BlockPos(0, 0, 0), new BlockPos(6, 0, 0));
        TunnelGeometry.Tunnel tunnel = TunnelGeometry.nearest(anchors, new Vec3(3.0D, 0.0D, 0.0D), LIMIT);
        assertNotNull(tunnel);
        Vec3 direction = tunnel.direction();
        assertEquals(1.0D, direction.length(), EPSILON);
        assertEquals(1.0D, direction.x, EPSILON);
    }

    @Test
    void aRayAcrossASegmentHitsIt() {
        Vec3 start = new Vec3(0.0D, 0.0D, 0.0D);
        Vec3 end = new Vec3(0.0D, 0.0D, 10.0D);
        TunnelGeometry.Aim aim = TunnelGeometry.aimAt(
                new Vec3(-5.0D, 0.0D, 5.0D), new Vec3(1.0D, 0.0D, 0.0D), start, end);
        assertEquals(5.0D, aim.along(), EPSILON);
        assertEquals(0.0D, aim.distanceSq(), EPSILON);
    }

    @Test
    void aRayBesideASegmentKeepsItsOffset() {
        Vec3 start = new Vec3(0.0D, 0.0D, 0.0D);
        Vec3 end = new Vec3(0.0D, 0.0D, 10.0D);
        TunnelGeometry.Aim aim = TunnelGeometry.aimAt(
                new Vec3(-5.0D, 3.0D, 5.0D), new Vec3(1.0D, 0.0D, 0.0D), start, end);
        assertEquals(5.0D, aim.along(), EPSILON);
        assertEquals(9.0D, aim.distanceSq(), EPSILON);
    }

    @Test
    void aRayPointingAwayDoesNotReachBehindItself() {
        Vec3 start = new Vec3(0.0D, 0.0D, 0.0D);
        Vec3 end = new Vec3(0.0D, 0.0D, 10.0D);
        TunnelGeometry.Aim aim = TunnelGeometry.aimAt(
                new Vec3(5.0D, 0.0D, 5.0D), new Vec3(1.0D, 0.0D, 0.0D), start, end);
        assertEquals(0.0D, aim.along(), EPSILON);
        assertEquals(25.0D, aim.distanceSq(), EPSILON);
    }
}
