package cc.sighs.dreamworld.dream;

import net.minecraft.world.entity.Entity;

/**
 * Creatures of the dream layer take no damage at all.
 *
 * <p>A dream creature is a projection: it can be moved across the mirror with the sword, but nothing
 * hurts it where it stands. Falls, drowning, lava, fire and hits all bounce off, so a zombie carried
 * into the dream keeps its health whatever happens around it.</p>
 *
 * <p>The adapter runs this last on purpose. A lethal dream-alloy sword hit is not damage either -
 * the sword hook has already cancelled it and moved the creature to the other layer by then - so
 * this only sees the damage that is left over.</p>
 */
public final class DreamDamageRules {
    private DreamDamageRules() {
    }

    /** @return whether the incoming damage must be cancelled */
    public static boolean shouldCancel(Entity entity) {
        return !entity.level.isClientSide && DreamEntities.isDreamOnly(entity);
    }
}
