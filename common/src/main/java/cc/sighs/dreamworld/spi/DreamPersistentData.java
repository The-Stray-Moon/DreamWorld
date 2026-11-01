package cc.sighs.dreamworld.spi;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

/**
 * Persistent, mod-scoped data attached to an entity.
 *
 * <p>Forge and NeoForge expose this directly on {@link Entity}; Fabric does not. The target decides
 * how the compound is stored and how it survives a reload.</p>
 */
public interface DreamPersistentData {
    /** @return the mod's root tag of the entity, created on demand */
    CompoundTag get(Entity entity);
}
