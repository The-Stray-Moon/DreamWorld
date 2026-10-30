package cc.sighs.dreamworld.client;

import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * Client-only dream state, written by {@link DWClientPayloadHandler} and read by renderers.
 */
public final class DreamClientState {
    private static volatile boolean dreaming;
    private static volatile List<BlockPos> graffiti = List.of();
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
}
