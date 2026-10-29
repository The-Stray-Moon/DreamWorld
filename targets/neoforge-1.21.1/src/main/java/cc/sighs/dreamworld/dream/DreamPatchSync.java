package cc.sighs.dreamworld.dream;

import cc.sighs.dreamworld.DreamWorldNeoForge121;
import cc.sighs.dreamworld.net.DWPayloads;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Pushes dream patches to the clients that need them.
 *
 * <p>Only dreaming players receive patches: their client overlays the dream states on top of the
 * real chunks, while everybody else keeps seeing the untouched real world. Chunks are sent when a
 * dreamer starts watching them, and again at the end of a tick whenever the server changed their
 * patch set.</p>
 */
@EventBusSubscriber(modid = DreamWorldNeoForge121.MOD_ID)
public final class DreamPatchSync {
    private static final Map<ResourceKey<Level>, Set<Long>> DIRTY = new HashMap<>();

    private DreamPatchSync() {
    }

    public static void sendChunk(ServerPlayer player, ServerLevel level, ChunkPos chunk) {
        Map<BlockPos, BlockState> patch = DreamPatches.get(level).forChunk(chunk);
        List<DWPayloads.PatchEntry> entries = new ArrayList<>(patch.size());
        patch.forEach((pos, state) ->
                entries.add(new DWPayloads.PatchEntry(pos.asLong(), NbtUtils.writeBlockState(state))));
        PacketDistributor.sendToPlayer(player, new DWPayloads.DreamPatchPayload(chunk.toLong(), entries));
    }

    /** Sends every patch the player could currently see. */
    public static void sendAround(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        int radius = player.getServer() == null ? 8 : player.getServer().getPlayerList().getViewDistance() + 1;
        ChunkPos center = new ChunkPos(player.blockPosition());
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                sendChunk(player, level, new ChunkPos(center.x + dx, center.z + dz));
            }
        }
    }

    /** Queues the chunk that contains {@code pos} for an end-of-tick resend. */
    public static void markDirty(ServerLevel level, BlockPos pos) {
        DIRTY.computeIfAbsent(level.dimension(), key -> new HashSet<>()).add(ChunkPos.asLong(pos));
    }

    @SubscribeEvent
    public static void onChunkWatch(ChunkWatchEvent.Watch event) {
        if (DreamManager.isDreaming(event.getPlayer())) {
            sendChunk(event.getPlayer(), event.getLevel(), event.getPos());
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 10 == 0) {
            for (ServerLevel level : event.getServer().getAllLevels()) {
                for (ServerPlayer player : level.players()) {
                    DreamManager.reconcileDreamMovement(player);
                }
            }
        }
        if (DIRTY.isEmpty()) {
            return;
        }
        Map<ResourceKey<Level>, Set<Long>> work = new HashMap<>(DIRTY);
        DIRTY.clear();
        work.forEach((dimension, chunks) -> {
            ServerLevel level = event.getServer().getLevel(dimension);
            if (level == null) {
                return;
            }
            for (long chunkLong : chunks) {
                ChunkPos chunk = new ChunkPos(chunkLong);
                int radius = event.getServer().getPlayerList().getViewDistance() + 1;
                for (ServerPlayer player : level.players()) {
                    ChunkPos watched = player.chunkPosition();
                    int dx = Math.abs(watched.x - chunk.x);
                    int dz = Math.abs(watched.z - chunk.z);
                    if (DreamManager.isDreaming(player) && Math.max(dx, dz) <= radius) {
                        sendChunk(player, level, chunk);
                    }
                }
            }
        });
    }
}
