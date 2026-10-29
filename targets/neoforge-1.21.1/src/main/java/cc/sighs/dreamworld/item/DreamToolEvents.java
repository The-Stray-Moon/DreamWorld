package cc.sighs.dreamworld.item;

import cc.sighs.dreamworld.DWRegistry;
import cc.sighs.dreamworld.DreamWorldNeoForge121;
import cc.sighs.dreamworld.block.DreamGraffitiBlock;
import cc.sighs.dreamworld.dream.DreamIntrusion;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Dream intrusion hooks for the dream alloy tool set.
 *
 * <p>Dream alloy gear never really destroys anything on the layer it is used in: a broken block is
 * carried over to the other layer, a creature whose last hit comes from the sword is moved there
 * alive and asleep. Both keep their normal, event-less transfer semantics: no loot, no experience,
 * no chained intrusion.</p>
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
        if (!isHoldingTool(player)) {
            return;
        }
        BlockPos pos = event.getPos();
        if (event.getLevel().getBlockState(pos).getBlock() instanceof DreamGraffitiBlock) {
            return;
        }
        // While dreaming the break only exists in the dream patch; in the real world the block is
        // removed without loot and the recorder keeps its old state for the dream layer.
        DreamIntrusion.breakBlock(player, level, pos);
        event.setCanceled(true);
        if (!cc.sighs.dreamworld.dream.DreamManager.isDreaming(player)) {
            level.removeBlock(pos, false);
        }
    }

    /**
     * A lethal hit from the dream alloy sword does not kill: the creature is moved to the other
     * layer, asleep and inert. Cancelling the damage keeps it out of death events, loot and XP.
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
        if (!isHoldingTool(player)) {
            return;
        }
        for (BlockPos pos : positions) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.getBlock() instanceof DreamGraffitiBlock) {
                continue;
            }
            DreamIntrusion.breakBlock(player, level, pos);
            if (!cc.sighs.dreamworld.dream.DreamManager.isDreaming(player)) {
                level.removeBlock(pos, false);
            }
        }
    }

    private static boolean isHoldingTool(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        return main.is(DWRegistry.DREAM_ALLOY_SWORD.get())
                || main.is(DWRegistry.DREAM_ALLOY_SHOVEL.get())
                || main.is(DWRegistry.DREAM_ALLOY_WAND.get());
    }
}
