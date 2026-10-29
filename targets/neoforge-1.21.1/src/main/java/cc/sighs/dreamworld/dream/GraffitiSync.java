package cc.sighs.dreamworld.dream;

import cc.sighs.dreamworld.DreamWorldNeoForge121;
import cc.sighs.dreamworld.net.DWPayloads;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Pushes the graffiti coordinate list to the clients, which render the tunnels between them.
 *
 * <p>The dream world shares the block data of the real world, so a list only changes when a
 * graffiti is placed or removed. Those two moments broadcast the whole list; login and dimension
 * changes resend it because a fresh client starts with an empty list.</p>
 */
@EventBusSubscriber(modid = DreamWorldNeoForge121.MOD_ID)
public final class GraffitiSync {
    private GraffitiSync() {
    }

    /** Coordinates of every registered graffiti; both layers share the same coordinates. */
    public static List<BlockPos> positionsIn(ServerLevel level) {
        return GraffitiRegistry.get(level).all().stream().map(GraffitiRegistry.Entry::pos).toList();
    }

    /** Sends the list of {@code level} to every connected player. */
    public static void broadcast(ServerLevel level) {
        PacketDistributor.sendToAllPlayers(new DWPayloads.GraffitiListPayload(positionsIn(level)));
    }

    /** Sends the list of the dimension the player is in right now. */
    public static void sync(ServerPlayer player) {
        if (player.level() instanceof ServerLevel level) {
            PacketDistributor.sendToPlayer(player, new DWPayloads.GraffitiListPayload(positionsIn(level)));
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }
}
