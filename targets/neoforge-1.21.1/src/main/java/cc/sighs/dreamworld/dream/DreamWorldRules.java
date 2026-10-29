package cc.sighs.dreamworld.dream;

import cc.sighs.dreamworld.DWRegistry;
import cc.sighs.dreamworld.DreamWorldNeoForge121;
import cc.sighs.dreamworld.entity.DreamSpearEntity;
import cc.sighs.dreamworld.item.DreamGraffitiItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * World rules of the dream world.
 *
 * <p>The dream world shares its block data with the real world, so a dreamer must not edit it with
 * bare hands. Breaking, placing and interacting are cancelled for dreaming players, except for
 * dream graffiti (the only block that can be built with inside a dream) and anything done while
 * holding a piece of dream alloy equipment, which is exactly what dream intrusions are made
 * with.</p>
 */
@EventBusSubscriber(modid = DreamWorldNeoForge121.MOD_ID)
public final class DreamWorldRules {
    private DreamWorldRules() {
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !DreamManager.isDreaming(player)) {
            return;
        }
        if (isGraffiti(event.getLevel().getBlockState(event.getPos())) || holdsDreamAlloy(player)) {
            return;
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !DreamManager.isDreaming(player)) {
            return;
        }
        BlockState clicked = event.getLevel().getBlockState(event.getPos());
        if (isGraffiti(clicked) || holdsDreamAlloy(player)) {
            return;
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !DreamManager.isDreaming(player)) {
            return;
        }
        BlockPos pos = event.getPos();
        if (isGraffiti(event.getLevel().getBlockState(pos))
                || holdsDreamAlloy(player)
                || holdsGraffiti(player)) {
            return;
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !DreamManager.isDreaming(player)) {
            return;
        }
        if (holdsDreamAlloy(player) || holdsGraffiti(player)) {
            return;
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !DreamManager.isDreaming(player)) {
            return;
        }
        // The spear is the anchor itself: a dreamer has to be able to pick their own spear back up.
        if (holdsDreamAlloy(player) || event.getTarget() instanceof DreamSpearEntity) {
            return;
        }
        event.setCanceled(true);
    }

    private static boolean isGraffiti(BlockState state) {
        return state.is(DWRegistry.DREAM_GRAFFITI.get());
    }

    private static boolean holdsDreamAlloy(Player player) {
        return isDreamAlloy(player.getMainHandItem()) || isDreamAlloy(player.getOffhandItem());
    }

    private static boolean isDreamAlloy(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        return item == DWRegistry.DREAM_ALLOY_SWORD.get()
                || item == DWRegistry.DREAM_ALLOY_SHOVEL.get()
                || item == DWRegistry.DREAM_ALLOY_WAND.get()
                || item == DWRegistry.DREAM_ALLOY_SPEAR.get();
    }

    private static boolean holdsGraffiti(Player player) {
        return player.getMainHandItem().getItem() instanceof DreamGraffitiItem
                || player.getOffhandItem().getItem() instanceof DreamGraffitiItem;
    }
}
