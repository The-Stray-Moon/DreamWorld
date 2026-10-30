package cc.sighs.dreamworld.client;

import cc.sighs.dreamworld.net.DWPayloads;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
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
 */
public final class DreamClientPatches {
    private static final Map<Long, Map<BlockPos, BlockState>> PATCHES = new HashMap<>();
    private static final Map<BlockPos, BlockState> REAL_STATES = new HashMap<>();
    private static boolean applying;

    private DreamClientPatches() {
    }

    /** Stores the patch set of a chunk and applies it when the player is dreaming. */
    public static void accept(long chunkLong, List<DWPayloads.PatchEntry> entries) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Map<BlockPos, BlockState> patch = new HashMap<>();
        for (DWPayloads.PatchEntry entry : entries) {
            patch.put(BlockPos.of(entry.pos()),
                    NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), entry.state()));
        }
        PATCHES.put(chunkLong, patch);
        if (DreamClientState.isDreaming()) {
            applyChunk(chunkLong);
        }
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
     * Called after the server applied a block update: the fresh real state is remembered and the
     * dream state is put back on top.
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
        REAL_STATES.put(pos.immutable(), level.getBlockState(pos));
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
            if (!applying) {
                // The first observation is the real state; later passes may already see the patch.
                REAL_STATES.putIfAbsent(pos.immutable(), level.getBlockState(pos));
            }
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
