package cc.sighs.dreamworld.boat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Pure geometry of the graffiti tunnels: which pairs of graffiti are linked and which of those
 * segments is closest to a point. Kept free of levels and entities so it can be unit tested; the
 * boat mixin and the client renderer both consume it.
 */
public final class TunnelGeometry {
    /** Same budget as the renderer, so a boat only locks onto a tunnel that is actually drawn. */
    public static final int MAX_PAIRS = 64;

    private TunnelGeometry() {
    }

    /** A tunnel between two graffiti blocks. */
    public record Pair(BlockPos from, BlockPos to) {
    }

    /**
     * The tunnel segments that are drawn: every pair while there are few enough graffiti, otherwise
     * each block linked to its nearest neighbour, shortest segments first, capped at
     * {@link #MAX_PAIRS}. The renderer and the boat share this, so a boat can only lock onto a
     * tunnel that is visible.
     */
    public static List<Pair> pairs(List<BlockPos> positions) {
        int size = positions.size();
        if (size < 2) {
            return List.of();
        }
        List<Pair> pairs = new ArrayList<>();
        long pairCount = (long) size * (size - 1L) / 2L;
        if (pairCount <= MAX_PAIRS) {
            for (int i = 0; i < size; i++) {
                for (int j = i + 1; j < size; j++) {
                    pairs.add(new Pair(positions.get(i), positions.get(j)));
                }
            }
            return List.copyOf(pairs);
        }
        List<Pair> candidates = new ArrayList<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (int i = 0; i < size; i++) {
            int nearest = -1;
            double nearestDistance = Double.MAX_VALUE;
            for (int j = 0; j < size; j++) {
                if (i == j) {
                    continue;
                }
                double distance = positions.get(i).distSqr(positions.get(j));
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = j;
                }
            }
            if (nearest < 0) {
                continue;
            }
            int low = Math.min(i, nearest);
            int high = Math.max(i, nearest);
            if (seen.add(((long) low << 32) | high)) {
                candidates.add(new Pair(positions.get(low), positions.get(high)));
            }
        }
        candidates.sort(java.util.Comparator.comparingDouble(pair -> pair.from().distSqr(pair.to())));
        return List.copyOf(candidates.subList(0, Math.min(MAX_PAIRS, candidates.size())));
    }

    /**
     * Closest tunnel segment to {@code point}.
     *
     * @return the best segment, or {@code null} when fewer than two graffiti exist
     */
    public static Tunnel nearest(List<BlockPos> positions, Vec3 point) {
        Tunnel best = null;
        for (Pair pair : pairs(positions)) {
            best = closer(best, Vec3.atCenterOf(pair.from()), Vec3.atCenterOf(pair.to()), point);
        }
        return best;
    }

    private static Tunnel closer(Tunnel best, Vec3 from, Vec3 to, Vec3 point) {
        Vec3 segment = to.subtract(from);
        double lengthSqr = segment.lengthSqr();
        if (lengthSqr < 0.25D) {
            return best;
        }
        double t = Mth.clamp(point.subtract(from).dot(segment) / lengthSqr, 0.0D, 1.0D);
        double distanceSq = point.distanceToSqr(from.add(segment.scale(t)));
        if (best == null || distanceSq < best.distanceSq()) {
            return new Tunnel(from, to, t, distanceSq);
        }
        return best;
    }

    /** One candidate tunnel. {@code t} is the projection of the point on the segment, 0..1. */
    public record Tunnel(Vec3 start, Vec3 end, double t, double distanceSq) {
        public Vec3 projection() {
            return start.add(end.subtract(start).scale(t));
        }

        public Vec3 direction() {
            Vec3 segment = end.subtract(start);
            double length = segment.length();
            return length < 1.0E-6D ? Vec3.ZERO : segment.scale(1.0D / length);
        }

        public double length() {
            return end.subtract(start).length();
        }

        public double remaining(double sign) {
            return sign < 0.0D ? length() * t : length() * (1.0D - t);
        }
    }
}
