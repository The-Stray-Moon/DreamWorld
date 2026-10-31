package cc.sighs.dreamworld.dream;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
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
 */
public final class DreamPatches extends SavedData {
    public static final String FILE_ID = "dreamworld_patches";
    private static final SavedData.Factory<DreamPatches> FACTORY =
            new SavedData.Factory<>(DreamPatches::new, DreamPatches::load, null);

    private final Map<Long, Map<BlockPos, BlockState>> byChunk = new HashMap<>();

    public static DreamPatches get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    /** Patches of one chunk; empty when nothing ever changed there. */
    public Map<BlockPos, BlockState> forChunk(ChunkPos chunk) {
        return byChunk.getOrDefault(chunk.toLong(), Map.of());
    }

    /** Patches of the chunk that contains {@code pos}. */
    public Map<BlockPos, BlockState> forChunkOf(BlockPos pos) {
        return forChunk(new ChunkPos(pos));
    }

    /** The state the dream layer shows at {@code pos}: the patch when present, else the real state. */
    public BlockState stateAt(ServerLevel level, BlockPos pos) {
        return forChunkOf(pos).getOrDefault(pos, level.getBlockState(pos));
    }

    /**
     * Remembers the state that a real-world edit is about to overwrite. The first edit of a
     * position wins, so the dream always keeps the oldest known state.
     */
    public void recordRealEdit(ServerLevel level, BlockPos pos, BlockState previous) {
        byChunk.computeIfAbsent(ChunkPos.asLong(pos), key -> new HashMap<>())
                .putIfAbsent(pos.immutable(), previous);
        setDirty();
        DreamPatchSync.markDirty(level, pos);
    }

    /** Writes a dream-only state, used by dream-side intrusions. */
    public void setDreamState(ServerLevel level, BlockPos pos, BlockState state) {
        byChunk.computeIfAbsent(ChunkPos.asLong(pos), key -> new HashMap<>())
                .put(pos.immutable(), state);
        setDirty();
        DreamPatchSync.markDirty(level, pos);
    }

    private static DreamPatches load(CompoundTag tag, HolderLookup.Provider registries) {
        DreamPatches patches = new DreamPatches();
        var blockLookup = registries.lookupOrThrow(Registries.BLOCK);
        ListTag chunks = tag.getList("chunks", Tag.TAG_COMPOUND);
        for (int i = 0; i < chunks.size(); i++) {
            CompoundTag chunkTag = chunks.getCompound(i);
            Map<BlockPos, BlockState> entries = new HashMap<>();
            ListTag list = chunkTag.getList("entries", Tag.TAG_COMPOUND);
            for (int j = 0; j < list.size(); j++) {
                CompoundTag entry = list.getCompound(j);
                entries.put(BlockPos.of(entry.getLong("pos")),
                        NbtUtils.readBlockState(blockLookup, entry.getCompound("state")));
            }
            patches.byChunk.put(chunkTag.getLong("pos"), entries);
        }
        return patches;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag chunks = new ListTag();
        byChunk.forEach((chunkPos, entries) -> {
            if (entries.isEmpty()) {
                return;
            }
            CompoundTag chunkTag = new CompoundTag();
            chunkTag.putLong("pos", chunkPos);
            ListTag list = new ListTag();
            entries.forEach((pos, state) -> {
                CompoundTag entry = new CompoundTag();
                entry.putLong("pos", pos.asLong());
                entry.put("state", NbtUtils.writeBlockState(state));
                list.add(entry);
            });
            chunkTag.put("entries", list);
            chunks.add(chunkTag);
        });
        tag.put("chunks", chunks);
        return tag;
    }
}
