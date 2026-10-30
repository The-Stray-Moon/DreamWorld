package cc.sighs.dreamworld.dream;

import cc.sighs.dreamworld.DWRegistry;
import cc.sighs.dreamworld.DreamWorldNeoForge121;
import cc.sighs.dreamworld.entity.DreamSpearEntity;
import cc.sighs.dreamworld.item.DreamGraffitiItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.BoatItem;
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
 *
 * <p>Boats are the vehicle of the dream layer: a dreamer may place one and use it, so boat items
 * and boat entities are let through. No other item gains that allowance.</p>
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
                || holdsGraffiti(player)
                || holdsBoat(player)) {
            return;
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !DreamManager.isDreaming(player)) {
            return;
        }
        if (holdsDreamAlloy(player) || holdsGraffiti(player) || holdsBoat(player)) {
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
        // A boat is the other way to travel the dream layer, so boarding and using one is allowed;
        // a chest boat keeps its container that way too.
        if (holdsDreamAlloy(player) || event.getTarget() instanceof DreamSpearEntity
                || isBoat(event.getTarget())) {
            return;
        }
        event.setCanceled(true);
    }

    private static boolean isGraffiti(BlockState state) {
        return state.is(DWRegistry.DREAM_GRAFFITI.get());
    }

    /**
     * Any boat item, chest variants included: they are all {@link BoatItem}s, while no other item
     * is, so the dream layer only gains the boat.
     */
    private static boolean holdsBoat(Player player) {
        return player.getMainHandItem().getItem() instanceof BoatItem
                || player.getOffhandItem().getItem() instanceof BoatItem;
    }

    /** 1.21.1 has no {@code AbstractBoat}; {@code ChestBoat} is a {@link Boat} subclass already. */
    private static boolean isBoat(Entity entity) {
        return entity instanceof Boat;
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
