package cc.sighs.dreamworld.item;

import cc.sighs.dreamworld.DreamWorldNeoForge121;
import cc.sighs.dreamworld.block.DreamGraffitiBlock;
import cc.sighs.dreamworld.dream.DreamIntrusion;
import cc.sighs.dreamworld.dream.DreamManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Dream intrusion hooks for the dream alloy tool set.
 *
 * <p>The set is split by job, and each half is the only one that may do it: the shovel breaks blocks
 * and its breaks become intrusions, the sword hits creatures and its kills become intrusions.
 * Nothing else carries a block or a creature across the mirror. Both keep their normal, event-less
 * transfer semantics: no loot, no experience, no chained intrusion.</p>
 */
@EventBusSubscriber(modid = DreamWorldNeoForge121.MOD_ID)
public final class DreamToolEvents {
    private DreamToolEvents() {
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        if (event.getLevel().getBlockState(pos).getBlock() instanceof DreamGraffitiBlock) {
            return;
        }
        // Only the shovel intrudes. Any other break stays vanilla, including the ones done with the
        // sword and the wand, and including breaks that the dream rules already refused upstream.
        if (!DreamIntrusion.isHoldingShovel(player)) {
            return;
        }
        // The break is replaced by an intrusion: a dream-side break carries the dream block into
        // reality, a real-side break carries the real block into the dream. Cancelling keeps vanilla
        // from dropping the block or spawning experience while the intrusion runs.
        event.setCanceled(true);
        if (DreamManager.isDreaming(player)) {
            // Only the patch changes, and the patch resend happens at the end of this tick anyway.
            DreamIntrusion.breakBlock(player, level, pos);
            return;
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
    }

    /**
     * A lethal hit from the dream alloy sword does not kill on either side of the mirror: the
     * creature is put to sleep where it stands, alive and inert. Cancelling the damage keeps it out
     * of death events, loot and XP. This hook serves the dreaming and the real-world hit alike, so
     * there is no second path that could process the same creature twice.
     *
     * <p>Only a sword hit reaches this path. Any other killing blow goes on to be a normal death,
     * which is what keeps a non-sword kill from carrying its victim into the dream.</p>
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof Player || !event.getEntity().isAlive()) {
            return;
        }
        Entity direct = event.getSource().getDirectEntity();
        if (!(direct instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!DreamIntrusion.isHoldingSword(player)) {
            return;
        }
        if (event.getEntity().getHealth() - event.getAmount() > 0.0F) {
            return;
        }
        event.setCanceled(true);
        DreamIntrusion.transferLiving(player, level, event.getEntity());
    }

    /**
     * Transfers a batch of blocks, e.g. everything the wand explosion caught.
     *
     * <p>This is the wand's own path, not mining: the blast replaces the blocks it caught instead of
     * breaking them normally. Only the wand reaches it, so a shovel break can never be handled twice.
     * </p>
     */
    static void handleBlocksBroken(ServerPlayer player, ServerLevel level, Iterable<BlockPos> positions) {
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
