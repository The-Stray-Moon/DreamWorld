package cc.sighs.dreamworld.client;

import cc.sighs.dreamworld.net.DreamPackets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Client-side dream overlay.
 *
 * <p>The client receives the real chunks from the server as usual, then applies the dream patch set
 * of every received chunk. The patched states are kept in this class and re-applied whenever the
 * server sends a block update for a patched position, so the dream view never flickers back to the
 * real world. Leaving the dream restores the latest real states that were observed.</p>
 *
 * <p>1.18.2 reads block states straight from a compound tag; there is no registry holder lookup.</p>
 */
public final class DreamClientPatches {
    private static final Map<Long, Map<BlockPos, BlockState>> PATCHES = new HashMap<>();
    private static final Map<BlockPos, BlockState> REAL_STATES = new HashMap<>();
    /** Positions whose dream block was carried in by an intrusion and therefore keeps its colour. */
    private static final java.util.Set<BlockPos> INTRUDED = new java.util.HashSet<>();
    private static boolean applying;

    private DreamClientPatches() {
    }

    /** Stores the patch set of a chunk and applies it when the player is dreaming. */
    public static void accept(long chunkLong, List<DreamPackets.PatchEntry> entries) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Map<BlockPos, BlockState> patch = new HashMap<>();
        for (DreamPackets.PatchEntry entry : entries) {
            BlockPos pos = BlockPos.of(entry.pos()).immutable();
            patch.put(pos, NbtUtils.readBlockState(entry.state()));
            // The real state comes from the server: the position already holds the dream block (or a
            // prediction of a break) by now, so reading it here would cache a guess.
            REAL_STATES.put(pos, NbtUtils.readBlockState(entry.realState()));
            if (entry.intruded()) {
                INTRUDED.add(pos);
            } else {
                INTRUDED.remove(pos);
            }
        }
        PATCHES.put(chunkLong, patch);
        if (DreamClientState.isDreaming()) {
            applyChunk(chunkLong);
        }
    }

    /** {@return whether the dream block at {@code pos} was carried in by an intrusion} */
    public static boolean isIntruded(BlockPos pos) {
        return !INTRUDED.isEmpty() && INTRUDED.contains(pos);
    }

    /**
     * {@return whether this client has been told what the dream layer shows in {@code chunk}}
     *
     * <p>An empty patch set still counts: the server sends every chunk it sends chunks for, so a
     * known-but-empty entry means "this chunk is identical on both layers", which is a real answer
     * and not a missing one.</p>
     */
    public static boolean knowsChunk(ChunkPos chunk) {
        return PATCHES.containsKey(chunk.toLong());
    }

    /**
     * Forgets every patch of the previous session.
     *
     * <p>The cache is static, so without this a client that disconnects while dreaming would apply
     * the old world's overlay to the next one on join - and a patch that turns the ground into air
     * under the player is exactly how a body ends up buried.</p>
     */
    public static void reset() {
        PATCHES.clear();
        REAL_STATES.clear();
        INTRUDED.clear();
    }

    /** Applies every known patch; called when the dream starts. */
    public static void applyAll() {
        for (long chunk : List.copyOf(PATCHES.keySet())) {
            applyChunk(chunk);
        }
    }

    /** Restores the latest known real states; called when the dream ends. */
    public static void restoreAll() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            // Chunks that unloaded meanwhile no longer hold the patched blocks; leave them alone.
            REAL_STATES.forEach((pos, state) -> {
                if (level.isLoaded(pos)) {
                    set(level, pos, state);
                }
            });
        }
        REAL_STATES.clear();
    }

    /**
     * Called after the server applied a block update: the dream state is put back on top.
     *
     * <p>The real state cached in {@link #REAL_STATES} is not touched here. It arrives from the
     * server with the patch, because the block update itself may be a local prediction of a break
     * (the dreamer's own mining) and would poison the cache that {@link #restoreAll} restores.</p>
     */
    public static void reassert(BlockPos pos) {
        if (!DreamClientState.isDreaming() || applying) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Map<BlockPos, BlockState> patch = PATCHES.get(ChunkPos.asLong(pos));
        if (patch == null) {
            return;
        }
        BlockState dream = patch.get(pos);
        if (dream == null) {
            return;
        }
        if (level.getBlockState(pos).getBlock()
                instanceof cc.sighs.dreamworld.block.DreamGraffitiBlock) {
            return;
        }
        if (!level.getBlockState(pos).equals(dream)) {
            set(level, pos, dream);
        }
    }

    public static void applyChunk(long chunkLong) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Map<BlockPos, BlockState> patch = PATCHES.get(chunkLong);
        if (patch == null) {
            return;
        }
        patch.forEach((pos, state) -> {
            if (!level.isLoaded(pos)) {
                return;
            }
            // Graffiti is shared by both sides, never overlay it with a stale patch.
            if (level.getBlockState(pos).getBlock()
                    instanceof cc.sighs.dreamworld.block.DreamGraffitiBlock) {
                return;
            }
            // The real state is not sampled here: it came with the patch (see accept).
            set(level, pos, state);
        });
    }

    private static void set(ClientLevel level, BlockPos pos, BlockState state) {
        if (level.getBlockState(pos).equals(state)) {
            return;
        }
        applying = true;
        try {
            level.setBlock(pos, state, Block.UPDATE_ALL);
        } finally {
            applying = false;
        }
    }
}
