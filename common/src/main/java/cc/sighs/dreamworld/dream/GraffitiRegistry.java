package cc.sighs.dreamworld.dream;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Server-side registry of every placed dream graffiti: UUID to coordinate binding.
 *
 * <p>Both "sides" (dream and real) share one entry, so a graffiti placed on either side is known
 * everywhere. The client receives the full coordinate list to render the tunnels.</p>
 */
public final class GraffitiRegistry extends SavedData {
    public static final String FILE_ID = "dreamworld_graffiti";
    private static final SavedData.Factory<GraffitiRegistry> FACTORY =
            new SavedData.Factory<>(GraffitiRegistry::new, GraffitiRegistry::load, null);

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    public record Entry(UUID id, BlockPos pos, String dimension) {
    }

    public static GraffitiRegistry get(ServerLevel level) {
        ServerLevel storageLevel = level.getServer().overworld();
        return storageLevel.getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    public static void add(ServerLevel level, UUID id, BlockPos pos) {
        GraffitiRegistry registry = get(level);
        registry.entries.put(id, new Entry(id, pos.immutable(), level.dimension().location().toString()));
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
        String dimension = level.dimension().location().toString();
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
        String dimension = level.dimension().location().toString();
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

    private static GraffitiRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        GraffitiRegistry registry = new GraffitiRegistry();
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            if (!entryTag.hasUUID("id")) {
                continue;
            }
            UUID id = entryTag.getUUID("id");
            registry.entries.put(id, new Entry(id, BlockPos.of(entryTag.getLong("pos")),
                    entryTag.getString("dimension")));
        }
        return registry;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry entry : entries.values()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID("id", entry.id());
            entryTag.putLong("pos", entry.pos().asLong());
            entryTag.putString("dimension", entry.dimension());
            list.add(entryTag);
        }
        tag.put("entries", list);
        return tag;
    }
}
