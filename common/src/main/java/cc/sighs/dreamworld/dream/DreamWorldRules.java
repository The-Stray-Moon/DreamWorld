package cc.sighs.dreamworld.dream;

import cc.sighs.dreamworld.DWObjects;
import cc.sighs.dreamworld.client.DreamClientState;
import cc.sighs.dreamworld.entity.DreamClayEntity;
import cc.sighs.dreamworld.entity.DreamSpearEntity;
import cc.sighs.dreamworld.item.DreamGraffitiItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * World rules of the dream layer, enforced on the client only.
 *
 * <p>A dreamer's own client refuses to break, place or interact with anything except dream
 * graffiti, dream alloy gear, boats, dream clay and the player's own spear. Nothing is checked on
 * the server; the layer is a client-side view, so the client is the authority for these rules.</p>
 */
public final class DreamWorldRules {
    private DreamWorldRules() {
    }

    /** True for the local dreaming player. */
    public static boolean isDreaming(Player player) {
        return player != null && player.level().isClientSide() && player.isLocalPlayer()
                && DreamClientState.isDreaming();
    }

    /**
     * Blocks may only be broken with the dream alloy shovel; graffiti is shared by both layers and
     * stays breakable by hand so it can always be removed.
     */
    public static boolean mayBreak(Player player, BlockState state) {
        return isGraffiti(state) || holdsShovel(player);
    }

    /** Only the dream alloy sword may hit a creature inside the dream layer. */
    public static boolean mayAttack(Player player, Entity target) {
        if (!isDreaming(player)) {
            return true;
        }
        if (target instanceof DreamSpearEntity || target instanceof DreamClayEntity
                || target instanceof Boat) {
            return true;
        }
        return holdsSword(player);
    }

    public static boolean mayInteractBlock(Player player, BlockState state) {
        return isGraffiti(state) || holdsDreamAlloy(player) || holdsGraffiti(player) || holdsBoat(player);
    }

    /**
     * Block interaction keyed by position and face, which is what the client entry points have: the
     * mixin hooks run before a hand-specific event exists.
     *
     * <p>The sleep gesture is exempt. Lying down to leave the dream is sneak + empty-hand + right
     * click on the top face of a block, and it is handled entirely on the server; refusing the
     * interaction on the client would drop the packet it needs. The gesture only intends to sleep,
     * and the server rejects it whenever it is not a valid gesture anyway.</p>
     */
    public static boolean mayInteractBlockAt(Player player, BlockPos pos, Direction face) {
        if (isSleepGesture(player, face)) {
            return true;
        }
        return mayInteractBlock(player, player.level().getBlockState(pos));
    }

    /** Sneak + right-click with an empty main hand onto the top face of a block. */
    public static boolean isSleepGesture(Player player, Direction face) {
        return face == Direction.UP && player.isShiftKeyDown() && player.getMainHandItem().isEmpty();
    }

    public static boolean mayUseItem(Player player) {
        return holdsDreamAlloy(player) || holdsGraffiti(player) || holdsBoat(player);
    }

    public static boolean mayInteractEntity(Player player, Entity target) {
        // A spear anchored on the other layer is a ghost: it is not there for this player.
        if (target instanceof DreamSpearEntity spear && spear.dreamSide() != DreamClientState.isDreaming()) {
            return false;
        }
        return holdsDreamAlloy(player) || target instanceof DreamSpearEntity
                || target instanceof DreamClayEntity || target instanceof Boat;
    }

    private static boolean isGraffiti(BlockState state) {
        return state.is(DWObjects.DREAM_GRAFFITI.get());
    }

    /** Any boat item, chest variants included. */
    private static boolean holdsBoat(Player player) {
        return player.getMainHandItem().getItem() instanceof BoatItem
                || player.getOffhandItem().getItem() instanceof BoatItem;
    }

    private static boolean holdsDreamAlloy(Player player) {
        return isDreamAlloy(player.getMainHandItem()) || isDreamAlloy(player.getOffhandItem());
    }

    /** The dream alloy shovel is the only block tool of the set. */
    private static boolean holdsShovel(Player player) {
        return player.getMainHandItem().is(DWObjects.DREAM_ALLOY_SHOVEL.get())
                || player.getOffhandItem().is(DWObjects.DREAM_ALLOY_SHOVEL.get());
    }

    /** The dream alloy sword is the only weapon of the set. */
    private static boolean holdsSword(Player player) {
        return player.getMainHandItem().is(DWObjects.DREAM_ALLOY_SWORD.get())
                || player.getOffhandItem().is(DWObjects.DREAM_ALLOY_SWORD.get());
    }

    private static boolean isDreamAlloy(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        return item == DWObjects.DREAM_ALLOY_SWORD.get()
                || item == DWObjects.DREAM_ALLOY_SHOVEL.get()
                || item == DWObjects.DREAM_ALLOY_WAND.get()
                || item == DWObjects.DREAM_ALLOY_SPEAR.get();
    }

    private static boolean holdsGraffiti(Player player) {
        return player.getMainHandItem().getItem() instanceof DreamGraffitiItem
                || player.getOffhandItem().getItem() instanceof DreamGraffitiItem;
    }
}
