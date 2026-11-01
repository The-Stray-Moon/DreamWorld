package cc.sighs.dreamworld.dream;

import cc.sighs.dreamworld.DreamWorld;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

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
 *
 * <p>26.1 stores saved data through a {@link Codec}: the map is flattened into one entry list and
 * rebuilt on load.</p>
 */
public final class DreamPatches extends SavedData {
    public static final Identifier FILE_ID = Identifier.fromNamespaceAndPath(DreamWorld.MOD_ID, "patches");

    /** {@code intruded} marks a block that an intrusion moved in from the other layer. */
    public record Patch(BlockState state, boolean intruded) {
    }

    private record StoredPatch(long chunk, long pos, BlockState state, boolean intruded) {
        static final Codec<StoredPatch> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("chunk").forGetter(StoredPatch::chunk),
                Codec.LONG.fieldOf("pos").forGetter(StoredPatch::pos),
                BlockState.CODEC.fieldOf("state").forGetter(StoredPatch::state),
                Codec.BOOL.optionalFieldOf("intruded", false).forGetter(StoredPatch::intruded))
                .apply(instance, StoredPatch::new));
    }

    private static final Codec<DreamPatches> CODEC = StoredPatch.CODEC.listOf()
            .xmap(DreamPatches::new, DreamPatches::toStored);

    public static final SavedDataType<DreamPatches> TYPE =
            new SavedDataType<>(FILE_ID, DreamPatches::new, CODEC, null);

    private final Map<Long, Map<BlockPos, Patch>> byChunk = new HashMap<>();

    public DreamPatches() {
    }

    private DreamPatches(List<StoredPatch> stored) {
        for (StoredPatch patch : stored) {
            byChunk.computeIfAbsent(patch.chunk(), key -> new HashMap<>())
                    .put(BlockPos.of(patch.pos()), new Patch(patch.state(), patch.intruded()));
        }
    }

    private List<StoredPatch> toStored() {
        List<StoredPatch> stored = new ArrayList<>();
        byChunk.forEach((chunkPos, entries) -> entries.forEach((pos, patch) ->
                stored.add(new StoredPatch(chunkPos, pos.asLong(), patch.state(), patch.intruded()))));
        return stored;
    }

    public static DreamPatches get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    /** Patches of one chunk; empty when nothing ever changed there. */
    public Map<BlockPos, Patch> forChunk(ChunkPos chunk) {
        return byChunk.getOrDefault(ChunkPos.pack(chunk.x(), chunk.z()), Map.of());
    }

    /** Patches of the chunk that contains {@code pos}. */
    public Map<BlockPos, Patch> forChunkOf(BlockPos pos) {
        return forChunk(ChunkPos.containing(pos));
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
        byChunk.computeIfAbsent(ChunkPos.pack(pos), key -> new HashMap<>())
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
        byChunk.computeIfAbsent(ChunkPos.pack(pos), key -> new HashMap<>())
                .put(pos.immutable(), new Patch(state, true));
        setDirty();
        DreamPatchSync.markDirty(level, pos);
    }
}
