package cc.sighs.dreamworld.client;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Client-side dream visibility.
 *
 * <p>A dreamer sees no ordinary creature at all (players are kept so several dreamers can meet),
 * the dream blub being the only wildlife of the dream layer. Which blub reaches which client is
 * decided on the server, so this class only hides the ordinary mobs and drops the blubs that are
 * still loaded when the dream ends.</p>
 */
public final class DreamEntityVisibility {
    private DreamEntityVisibility() {
    }

    /** @return whether the living entity must be hidden from the local dreamer */
    public static boolean shouldHide(LivingEntity entity) {
        if (!DreamClientState.isDreaming()) {
            return false;
        }
        if (entity instanceof Player) {
            return false;
        }
        // Dream-side creatures are the only living things a dreamer may see; everything else of the
        // real world is hidden. The server only sends dream creatures to dreamers anyway, this keeps
        // the shared real entities out of the view.
        return !DreamClientState.dreamEntityIds().contains(entity.getId());
    }
}
