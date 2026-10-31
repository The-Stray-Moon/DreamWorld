package cc.sighs.dreamworld.dream;

import cc.sighs.dreamworld.DreamWorldNeoForge121;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Creatures of the dream layer take no damage at all.
 *
 * <p>A dream creature is a projection: it can be moved across the mirror with the sword, but nothing
 * hurts it where it stands. Falls, drowning, lava, fire and hits all bounce off, so a zombie carried
 * into the dream keeps its health whatever happens around it.</p>
 *
 * <p>The handler runs last on purpose. A lethal dream-alloy sword hit is not damage either - the sword
 * hook has already cancelled it and moved the creature to the other layer by then - so this only sees
 * the damage that is left over.</p>
 */
@EventBusSubscriber(modid = DreamWorldNeoForge121.MOD_ID)
public final class DreamDamageRules {
    private DreamDamageRules() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide || !DreamEntities.isDreamOnly(entity)) {
            return;
        }
        event.setCanceled(true);
    }
}
