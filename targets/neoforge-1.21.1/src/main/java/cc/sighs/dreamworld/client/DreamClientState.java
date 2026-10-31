package cc.sighs.dreamworld.client;

import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * Client-only dream state, written by {@link DWClientPayloadHandler} and read by renderers.
 */
public final class DreamClientState {
    /**
     * Colour of the dream sky, rgb(73, 128, 146).
     *
     * <p>Used by the sky renderer. The water palette keeps its own scheme colour, so this is the
     * only place the sky's exact tone is defined.</p>
     */
    public static final net.minecraft.world.phys.Vec3 SKY_COLOR =
            new net.minecraft.world.phys.Vec3(73.0D / 255.0D, 128.0D / 255.0D, 146.0D / 255.0D);

    private static volatile boolean dreaming;
    private static volatile boolean anchorBound;
    private static volatile List<BlockPos> graffiti = List.of();
    private static volatile java.util.Set<java.util.UUID> dreamers = java.util.Set.of();
    private static volatile java.util.Set<Integer> dreamEntityIds = java.util.Set.of();

    private DreamClientState() {
    }

    public static boolean isDreaming() {
        return dreaming;
    }

    public static void setDreaming(boolean value) {
        dreaming = value;
    }

    public static List<BlockPos> graffiti() {
        return graffiti;
    }

    public static void setGraffiti(List<BlockPos> positions) {
        graffiti = List.copyOf(positions);
    }

    public static java.util.Set<Integer> dreamEntityIds() {
        return dreamEntityIds;
    }

    public static void setDreamEntityIds(List<Integer> ids) {
        dreamEntityIds = java.util.Set.copyOf(ids);
    }

    /** Whether the local player has a dream alloy spear anchored; mirrored from the server. */
    public static boolean isAnchorBound() {
        return anchorBound;
    }

    public static void setAnchorBound(boolean value) {
        anchorBound = value;
    }

    /** UUIDs of the players that are dreaming somewhere else, shown lying down on this client. */
    public static java.util.Set<java.util.UUID> dreamers() {
        return dreamers;
    }

    public static void setDreamers(List<java.util.UUID> ids) {
        dreamers = java.util.Set.copyOf(ids);
    }

    /** {@return whether the player with this UUID is dreaming, as far as this client knows} */
    public static boolean isDreamer(java.util.UUID id) {
        java.util.Set<java.util.UUID> current = dreamers;
        return !current.isEmpty() && current.contains(id);
    }

    /**
     * Drops every piece of dream state.
     *
     * <p>All of it is session state that arrives from the server, so a client that keeps it across a
     * disconnect starts the next world believing whatever the previous one said - a stale "dreaming"
     * flag would apply an old patch set to the new world before the server ever speaks.</p>
     */
    public static void reset() {
        dreaming = false;
        anchorBound = false;
        graffiti = List.of();
        dreamers = java.util.Set.of();
        dreamEntityIds = java.util.Set.of();
    }
}
