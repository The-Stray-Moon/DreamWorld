package cc.sighs.dreamworld.boat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Pure geometry of the graffiti tunnels: which pairs of graffiti are linked and which of those
 * segments is closest to a point. Kept free of levels and entities so it can be unit tested; the
 * boat mixin and the client renderer both consume it.
 */
public final class TunnelGeometry {
    private TunnelGeometry() {
    }

    /** A tunnel between two graffiti blocks. */
    public record Pair(BlockPos from, BlockPos to) {
    }

    /**
     * The tunnel segments that exist.
     *
     * <p>Every graffiti may form at most {@code maxPerGraffiti} tunnels, always with the closest
     * blocks it can get. The candidates are therefore ranked by length and accepted as long as both
     * ends still have a free slot, which keeps every block's edges among its nearest neighbours and
     * never exceeds the limit at either end of a tunnel.</p>
     *
     * <p>The renderer and the boat share this, so a boat can only attach to a tunnel that is drawn.
     * </p>
     */
    public static List<Pair> pairs(List<BlockPos> positions, int maxPerGraffiti) {
        int size = positions.size();
        if (size < 2 || maxPerGraffiti < 1) {
            return List.of();
        }
        List<Candidate> candidates = new ArrayList<>(size * (size - 1) / 2);
        for (int i = 0; i < size; i++) {
            for (int j = i + 1; j < size; j++) {
                candidates.add(new Candidate(i, j, positions.get(i).distSqr(positions.get(j))));
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::distanceSq));
        int[] degree = new int[size];
        List<Pair> pairs = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (degree[candidate.low()] >= maxPerGraffiti
                    || degree[candidate.high()] >= maxPerGraffiti) {
                continue;
            }
            degree[candidate.low()]++;
            degree[candidate.high()]++;
            pairs.add(new Pair(positions.get(candidate.low()), positions.get(candidate.high())));
        }
        return List.copyOf(pairs);
    }

    /** One possible tunnel between two graffiti and the distance that ranks it. */
    private record Candidate(int low, int high, double distanceSq) {
    }

    /** The tunnel for one specific pair, or {@code null} when that pair does not exist right now. */
    @Nullable
    public static Tunnel forPair(List<BlockPos> positions, Pair pair, Vec3 point, int maxPerGraffiti) {
        for (Pair candidate : pairs(positions, maxPerGraffiti)) {
            if (samePair(candidate, pair)) {
                return closer(null, candidate, Vec3.atCenterOf(candidate.from()),
                        Vec3.atCenterOf(candidate.to()), point);
            }
        }
        return null;
    }

    /** {@return whether both pairs name the same two graffiti, in either order} */
    public static boolean samePair(Pair first, Pair second) {
        return first.from().equals(second.from()) && first.to().equals(second.to())
                || first.from().equals(second.to()) && first.to().equals(second.from());
    }

    /**
     * Closest tunnel segment to {@code point}, optionally skipping one pair.
     *
     * @param exclude a pair not to consider, used to switch away from the line being ridden
     * @return the best segment, or {@code null} when fewer than two graffiti exist
     */
    @Nullable
    public static Tunnel nearest(List<BlockPos> positions, Vec3 point, int maxPerGraffiti,
            @Nullable Pair exclude) {
        Tunnel best = null;
        for (Pair pair : pairs(positions, maxPerGraffiti)) {
            if (exclude != null && samePair(pair, exclude)) {
                continue;
            }
            best = closer(best, pair, Vec3.atCenterOf(pair.from()), Vec3.atCenterOf(pair.to()), point);
        }
        return best;
    }

    /**
     * Closest tunnel segment to {@code point}.
     *
     * @return the best segment, or {@code null} when fewer than two graffiti exist
     */
    public static Tunnel nearest(List<BlockPos> positions, Vec3 point, int maxPerGraffiti) {
        return nearest(positions, point, maxPerGraffiti, null);
    }

    private static Tunnel closer(Tunnel best, Pair pair, Vec3 from, Vec3 to, Vec3 point) {
        Vec3 segment = to.subtract(from);
        double lengthSqr = segment.lengthSqr();
        if (lengthSqr < 0.25D) {
            return best;
        }
        double t = Mth.clamp(point.subtract(from).dot(segment) / lengthSqr, 0.0D, 1.0D);
        double distanceSq = point.distanceToSqr(from.add(segment.scale(t)));
        if (best == null || distanceSq < best.distanceSq()) {
            return new Tunnel(pair, from, to, t, distanceSq);
        }
        return best;
    }

    /**
     * One candidate tunnel. {@code t} is the projection of the point on the segment, 0..1.
     *
     * <p>The graffiti pair is carried along so a rider can tell whether the tunnel under the
     * crosshair is the one already being ridden.</p>
     */
    public record Tunnel(Pair pair, Vec3 start, Vec3 end, double t, double distanceSq) {
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

    /**
     * How close a ray passes to one segment.
     *
     * @param along how many blocks along the ray the closest approach lies; negative when the
     *              segment is behind the origin
     * @param distanceSq squared distance between the ray and the segment at that point
     */
    public record Aim(double along, double distanceSq) {
    }

    /**
     * Closest approach between a ray and a segment, used to tell what the crosshair is pointing at.
     *
     * <p>{@code direction} is expected to be normalised. The result is the pair of closest points
     * with the segment clamped to its own ends, so aiming past a graffiti does not count as aiming
     * at the tunnel behind it.</p>
     */
    public static Aim aimAt(Vec3 origin, Vec3 direction, Vec3 start, Vec3 end) {
        Vec3 segment = end.subtract(start);
        Vec3 toStart = origin.subtract(start);
        double a = direction.lengthSqr();
        double b = direction.dot(segment);
        double c = segment.lengthSqr();
        double d = direction.dot(toStart);
        double e = segment.dot(toStart);
        double denominator = a * c - b * b;
        double along;
        double t;
        if (denominator < 1.0E-9D) {
            // Ray and segment are parallel: measure from the segment start.
            along = -d;
            t = 0.0D;
        } else {
            t = Mth.clamp((a * e - b * d) / denominator, 0.0D, 1.0D);
            along = (b * t - d) / a;
            if (along < 0.0D) {
                along = 0.0D;
                t = Mth.clamp(e / c, 0.0D, 1.0D);
            }
        }
        Vec3 rayPoint = origin.add(direction.scale(along));
        Vec3 segmentPoint = start.add(segment.scale(t));
        return new Aim(along, rayPoint.distanceToSqr(segmentPoint));
    }
}
