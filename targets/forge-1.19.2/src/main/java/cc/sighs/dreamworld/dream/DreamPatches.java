package cc.sighs.dreamworld.dream;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Per-level dream patch storage.
 *
 * <p>The dream layer is not a world of its own: it is the real level plus this patch set. Whenever
 * a block in the real world changes, the state that was there before is remembered here, so the
 * dream layer keeps showing the terrain as it was generated. Dream-side edits overwrite the patch
 * instead. All players share the same patch set, which is why several dreamers see the same dream.</p>
 *
 * <p>A patch also remembers where it came from. The terrain patches are the dream's own world and
 * get its pink tint; a block an intrusion carried in from reality is an outsider and keeps the
 * colour it had there, which is why the flag is stored and synced with every patch.</p>
 */
public final class DreamPatches extends SavedData {
    public static final String FILE_ID = "dreamworld_patches";
    private static final String ENTRY_POS = "pos";
    private static final String ENTRY_STATE = "state";
    private static final String ENTRY_INTRUDED = "intruded";
    /** {@code intruded} marks a block that an intrusion moved in from the other layer. */
    public record Patch(BlockState state, boolean intruded) {
    }

    private final Map<Long, Map<BlockPos, Patch>> byChunk = new HashMap<>();

    public static DreamPatches get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(DreamPatches::load, DreamPatches::new,
                FILE_ID);
    }

    /** Patches of one chunk; empty when nothing ever changed there. */
    public Map<BlockPos, Patch> forChunk(ChunkPos chunk) {
        return byChunk.getOrDefault(chunk.toLong(), Map.of());
    }

    /** Patches of the chunk that contains {@code pos}. */
    public Map<BlockPos, Patch> forChunkOf(BlockPos pos) {
        return forChunk(new ChunkPos(pos));
    }

    /** The state the dream layer shows at {@code pos}: the patch when present, else the real state. */
    public BlockState stateAt(ServerLevel level, BlockPos pos) {
        Patch patch = forChunkOf(pos).get(pos);
        return patch == null ? level.getBlockState(pos) : patch.state();
    }

    /**
     * Remembers the state that a real-world edit is about to overwrite. The first edit of a
     * position wins, so the dream always keeps the oldest known state.
     *
     * <p>This is the dream's own terrain, so the entry is tintable.</p>
     */
    public void recordRealEdit(ServerLevel level, BlockPos pos, BlockState previous) {
        byChunk.computeIfAbsent(ChunkPos.asLong(pos), key -> new HashMap<>())
                .putIfAbsent(pos.immutable(), new Patch(previous, false));
        setDirty();
        DreamPatchSync.markDirty(level, pos);
    }

    /**
     * Writes a dream-only state. This is the intrusion path: the state either becomes air where a
     * dream block was taken away, or holds the block reality just lost, and an intruded block keeps
     * the colour it had on the side it came from.
     */
    public void setDreamState(ServerLevel level, BlockPos pos, BlockState state) {
        byChunk.computeIfAbsent(ChunkPos.asLong(pos), key -> new HashMap<>())
                .put(pos.immutable(), new Patch(state, true));
        setDirty();
        DreamPatchSync.markDirty(level, pos);
    }

    private static DreamPatches load(CompoundTag tag) {
        DreamPatches patches = new DreamPatches();
        ListTag chunks = tag.getList("chunks", Tag.TAG_COMPOUND);
        for (int i = 0; i < chunks.size(); i++) {
            CompoundTag chunkTag = chunks.getCompound(i);
            Map<BlockPos, Patch> entries = new HashMap<>();
            ListTag list = chunkTag.getList("entries", Tag.TAG_COMPOUND);
            for (int j = 0; j < list.size(); j++) {
                CompoundTag entry = list.getCompound(j);
                entries.put(BlockPos.of(entry.getLong(ENTRY_POS)), new Patch(
                        NbtUtils.readBlockState(entry.getCompound(ENTRY_STATE)),
                        entry.getBoolean(ENTRY_INTRUDED)));
            }
            patches.byChunk.put(chunkTag.getLong("pos"), entries);
        }
        return patches;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag chunks = new ListTag();
        byChunk.forEach((chunkPos, entries) -> {
            if (entries.isEmpty()) {
                return;
            }
            CompoundTag chunkTag = new CompoundTag();
            chunkTag.putLong("pos", chunkPos);
            ListTag list = new ListTag();
            entries.forEach((pos, patch) -> {
                CompoundTag entry = new CompoundTag();
                entry.putLong(ENTRY_POS, pos.asLong());
                entry.put(ENTRY_STATE, NbtUtils.writeBlockState(patch.state()));
                entry.putBoolean(ENTRY_INTRUDED, patch.intruded());
                list.add(entry);
            });
            chunkTag.put("entries", list);
            chunks.add(chunkTag);
        });
        tag.put("chunks", chunks);
        return tag;
    }
}
