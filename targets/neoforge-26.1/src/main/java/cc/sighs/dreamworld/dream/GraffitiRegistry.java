package cc.sighs.dreamworld.dream;

import cc.sighs.dreamworld.DreamWorld;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Server-side registry of every placed dream graffiti: UUID to coordinate binding.
 *
 * <p>Both "sides" (dream and real) share one entry, so a graffiti placed on either side is known
 * everywhere. The client receives the full coordinate list to render the tunnels.</p>
 *
 * <p>26.1 replaced {@code SavedData.Factory} with codec-based {@link SavedDataType}s.</p>
 */
public final class GraffitiRegistry extends SavedData {
    public static final Identifier FILE_ID = Identifier.fromNamespaceAndPath(DreamWorld.MOD_ID, "graffiti");

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    public record Entry(UUID id, BlockPos pos, String dimension) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Entry::id),
                BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
                Codec.STRING.fieldOf("dimension").forGetter(Entry::dimension))
                .apply(instance, Entry::new));
    }

    private static final Codec<GraffitiRegistry> CODEC = Entry.CODEC.listOf()
            .xmap(GraffitiRegistry::new, registry -> List.copyOf(registry.entries.values()));

    public static final SavedDataType<GraffitiRegistry> TYPE =
            new SavedDataType<>(FILE_ID, GraffitiRegistry::new, CODEC, null);

    public GraffitiRegistry() {
    }

    private GraffitiRegistry(List<Entry> entries) {
        for (Entry entry : entries) {
            this.entries.put(entry.id(), entry);
        }
    }

    public static GraffitiRegistry get(ServerLevel level) {
        ServerLevel storageLevel = level.getServer().overworld();
        return storageLevel.getDataStorage().computeIfAbsent(TYPE);
    }

    public static void add(ServerLevel level, UUID id, BlockPos pos) {
        GraffitiRegistry registry = get(level);
        registry.entries.put(id, new Entry(id, pos.immutable(), level.dimension().identifier().toString()));
        registry.setDirty();
    }

    public static void remove(Level level, UUID id) {
        if (id == null || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        GraffitiRegistry registry = get(serverLevel);
        if (registry.entries.remove(id) != null) {
            registry.setDirty();
        }
    }

    public boolean contains(UUID id) {
        return entries.containsKey(id);
    }

    public List<Entry> entriesIn(ServerLevel level) {
        String dimension = level.dimension().identifier().toString();
        List<Entry> result = new ArrayList<>();
        for (Entry entry : entries.values()) {
            if (entry.dimension().equals(dimension)) {
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * Distance in blocks to the nearest graffiti of {@code level}'s dimension.
     *
     * <p>Graffiti on the other layer share the coordinates of this one, so the same answer serves a
     * dreamer and an awake player. Returns {@link Double#MAX_VALUE} when there is none.</p>
     */
    public double nearestDistance(ServerLevel level, BlockPos pos) {
        String dimension = level.dimension().identifier().toString();
        double nearest = Double.MAX_VALUE;
        for (Entry entry : entries.values()) {
            if (!entry.dimension().equals(dimension)) {
                continue;
            }
            double distance = Math.sqrt(entry.pos().distSqr(pos));
            if (distance < nearest) {
                nearest = distance;
            }
        }
        return nearest;
    }

    public List<Entry> all() {
        return List.copyOf(entries.values());
    }
}
