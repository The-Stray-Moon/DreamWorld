package cc.sighs.dreamworld.dream;

import cc.sighs.dreamworld.DreamWorld;
import cc.sighs.dreamworld.net.DreamPackets;
import cc.sighs.dreamworld.spi.DreamPlatform;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Pushes the graffiti coordinate list to the clients, which render the tunnels between them.
 *
 * <p>The dream world shares the block data of the real world, so a list only changes when a
 * graffiti is placed or removed. Those two moments broadcast the whole list; login and dimension
 * changes resend it because a fresh client starts with an empty list.</p>
 */
public final class GraffitiSync {
    private GraffitiSync() {
    }

    /** Coordinates of every registered graffiti; both layers share the same coordinates. */
    public static List<BlockPos> positionsIn(ServerLevel level) {
        return GraffitiRegistry.get(level).all().stream().map(GraffitiRegistry.Entry::pos).toList();
    }

    /** Sends the list of {@code level} to every connected player. */
    public static void broadcast(ServerLevel level) {
        DreamPlatform.get().network().sendToAll(level.getServer(),
                new DreamPackets.GraffitiList(positionsIn(level),
                        DreamPlatform.get().config().maxTunnelsPerGraffiti()));
    }

    /** Sends the list of the dimension the player is in right now. */
    public static void sync(ServerPlayer player) {
        if (player.level instanceof ServerLevel level) {
            DreamPlatform.get().network().sendToPlayer(player, new DreamPackets.GraffitiList(
                    positionsIn(level), DreamPlatform.get().config().maxTunnelsPerGraffiti()));
        }
    }

    public static void onPlayerLoggedIn(ServerPlayer player) {
        sync(player);
    }

    public static void onPlayerChangedDimension(ServerPlayer player) {
        sync(player);
    }
}
