package cc.sighs.dreamworld.item;

import cc.sighs.dreamworld.DWRegistry;
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
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Dream intrusion hooks for the dream alloy tool set.
 *
 * <p>Dream alloy gear never really destroys anything on the layer it is used in: a broken block is
 * carried over to the other layer, a creature whose last hit comes from the sword is put to sleep
 * where it stands instead of dying. Both keep their normal, event-less transfer semantics: no loot,
 * no experience, no chained intrusion.</p>
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
        if (!DreamIntrusion.isHoldingTool(player)) {
            return;
        }
        BlockPos pos = event.getPos();
        if (event.getLevel().getBlockState(pos).getBlock() instanceof DreamGraffitiBlock) {
            return;
        }
        // The break is always replaced by an intrusion: a dream-side break carries the dream block
        // into reality, a real-side break carries the real block into the dream. Cancelling keeps
        // vanilla from dropping the block or spawning experience while the intrusion runs.
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
        if (!player.getMainHandItem().is(DWRegistry.DREAM_ALLOY_SWORD.get())) {
            return;
        }
        if (event.getEntity().getHealth() - event.getAmount() > 0.0F) {
            return;
        }
        event.setCanceled(true);
        DreamIntrusion.transferLiving(player, level, event.getEntity());
    }

    /** Transfers a batch of blocks, e.g. everything the wand explosion caught. */
    static void handleBlocksBroken(ServerPlayer player, ServerLevel level, Iterable<BlockPos> positions) {
        if (!DreamIntrusion.isHoldingTool(player)) {
            return;
        }
        for (BlockPos pos : positions) {
            // The guard is inside DreamIntrusion: it reads the layer the player stands on and skips
            // air as well as graffiti.
            DreamIntrusion.breakBlock(player, level, pos);
        }
    }
}
