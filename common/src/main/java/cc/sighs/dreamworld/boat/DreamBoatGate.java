package cc.sighs.dreamworld.boat;

import cc.sighs.dreamworld.client.DreamClientState;
import cc.sighs.dreamworld.dream.DreamManager;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;

/**
 * Decides whether a boat is currently a dream boat.
 *
 * <p>The two layers share one server world, so a boat is not an entity of one of them: it becomes a
 * dream boat while a dreaming player drives it, and stops being one when that player gets out. Every
 * dream-boat rule - the glide, the tunnels, the immunity to damage and the refusal to carry real
 * creatures - hangs off this single question, so it lives in one place instead of being copied into
 * each mixin.</p>
 *
 * <p>The gate has to answer on both sides: the owning client simulates the boat, the server owns the
 * damage and the boarding.</p>
 */
public final class DreamBoatGate {
    private DreamBoatGate() {
    }

    /** {@return whether {@code rider} is controlling a boat as a dreamer} */
    public static boolean isDreamRider(@Nullable Entity rider) {
        if (!(rider instanceof Player player)) {
            return false;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            return DreamManager.isDreaming(serverPlayer);
        }
        // DreamClientState only holds the flag that DreamManager mirrors to this client.
        return player.isLocalPlayer() && DreamClientState.isDreaming();
    }

    /** {@return whether {@code boat} is being driven as a dream boat} */
    public static boolean isDreamBoat(Boat boat) {
        return isDreamRider(boat.getControllingPassenger());
    }
}
