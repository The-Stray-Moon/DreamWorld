package cc.sighs.dreamworld.item;

import cc.sighs.dreamworld.block.DreamGraffitiBlock;
import cc.sighs.dreamworld.dream.DreamIntrusion;
import cc.sighs.dreamworld.dream.DreamManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Dream intrusion hooks for the dream alloy tool set.
 *
 * <p>The set is split by job, and each half is the only one that may do it: the shovel breaks blocks
 * and its breaks become intrusions, the sword hits creatures and its kills become intrusions.
 * Nothing else carries a block or a creature across the mirror. Both keep their normal, event-less
 * transfer semantics: no loot, no experience, no chained intrusion.</p>
 */
public final class DreamToolEvents {
    private DreamToolEvents() {
    }

    /** @return whether vanilla must be stopped from breaking the block itself */
    public static boolean onBlockBreak(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).getBlock() instanceof DreamGraffitiBlock) {
            return false;
        }
        // Only the shovel intrudes. Any other break stays vanilla, including the ones done with the
        // sword and the wand, and including breaks that the dream rules already refused upstream.
        if (!DreamIntrusion.isHoldingShovel(player)) {
            return false;
        }
        // The break is replaced by an intrusion: a dream-side break carries the dream block into
        // reality, a real-side break carries the real block into the dream. Cancelling keeps vanilla
        // from dropping the block or spawning experience while the intrusion runs.
        if (DreamManager.isDreaming(player)) {
            // Only the patch changes, and the patch resend happens at the end of this tick anyway.
            DreamIntrusion.breakBlock(player, level, pos);
            return true;
        }
        // NeoForge sends the "the block is still there" correction packet right after this handler
        // returns. Removing the block inline would be painted over by that packet, so the removal
        // waits one tick and stays the last word for the client.
        MinecraftServer server = player.getServer();
        if (server == null) {
            DreamIntrusion.breakBlock(player, level, pos);
        } else {
            server.execute(() -> DreamIntrusion.breakBlock(player, level, pos));
        }
        return true;
    }

    /**
     * A lethal hit from the dream alloy gear does not kill on either side of the mirror: the creature
     * is put to sleep where it stands, alive and inert. Cancelling the damage keeps it out of death
     * events, loot and XP. This hook serves the dreaming and the real-world hit alike, so there is no
     * second path that could process the same creature twice.
     *
     * <p>Two sources qualify: the sword's own hit, and the wand's blast, which is an explosion caused
     * by the player. Any other killing blow goes on to be a normal death, which is what keeps an
     * unrelated kill from carrying its victim across the mirror.</p>
     *
     * @return whether the damage must be cancelled because the creature crossed the mirror
     */
    public static boolean onIncomingDamage(LivingEntity target, DamageSource source, float amount) {
        if (target instanceof Player || !target.isAlive()) {
            return false;
        }
        if (!(source.getEntity() instanceof ServerPlayer player)
                || !(player.level instanceof ServerLevel level)) {
            return false;
        }
        if (!isIntrudingHit(source, player)) {
            return false;
        }
        if (target.getHealth() - amount > 0.0F) {
            return false;
        }
        DreamIntrusion.transferLiving(player, level, target);
        return true;
    }

    /** {@return whether this blow came from dream alloy gear that carries a kill across the mirror} */
    private static boolean isIntrudingHit(DamageSource source, ServerPlayer player) {
        // The sword hits directly, so the player is the direct entity of the damage.
        if (DreamIntrusion.isHoldingSword(player) && source.getDirectEntity() instanceof ServerPlayer) {
            return true;
        }
        // The wand damages through a player-caused explosion, which has no direct entity of its own.
        return DreamIntrusion.isHoldingWand(player) && source.isExplosion()
                && source.getEntity() instanceof ServerPlayer;
    }

    /**
     * Transfers a batch of blocks, e.g. everything the wand explosion caught.
     *
     * <p>This is the wand's own path, not mining: the blast replaces the blocks it caught instead of
     * breaking them normally. Only the wand reaches it, so a shovel break can never be handled twice.
     * </p>
     */
    public static void handleBlocksBroken(ServerPlayer player, ServerLevel level,
            Iterable<BlockPos> positions) {
        if (!DreamIntrusion.isHoldingWand(player)) {
            return;
        }
        for (BlockPos pos : positions) {
            // The guard is inside DreamIntrusion: it reads the layer the player stands on and skips
            // air as well as graffiti.
            DreamIntrusion.breakBlock(player, level, pos);
        }
    }
}
