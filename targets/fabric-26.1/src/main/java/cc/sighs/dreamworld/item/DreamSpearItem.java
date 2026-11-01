package cc.sighs.dreamworld.item;

import cc.sighs.dreamworld.DWObjects;
import cc.sighs.dreamworld.client.DreamClientState;
import cc.sighs.dreamworld.dream.DreamManager;
import cc.sighs.dreamworld.entity.DreamSpearEntity;
import cc.sighs.dreamworld.spi.DreamPlatform;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Dream alloy spear: a thrown anchor with no attack damage, permanently bound to one thrower.
 *
 * <p>Its use is the trident's, minus the enchantment paths: hold to charge, release to throw, and
 * the charge has to last {@link #THROW_THRESHOLD_TIME} ticks. The spear is spawned with the same
 * launch speed and aim spread as a trident, plays the same throw sound, is removed from the inventory
 * on the server unless the thrower has infinite materials, and the use is counted in the item-used
 * statistic on both sides. A player may keep one spear per layer in the world; the owner has to
 * pick it up again before throwing another one on that layer.</p>
 */
public class DreamSpearItem extends Item {
    /** Same charge threshold as the trident. */
    public static final int THROW_THRESHOLD_TIME = 10;
    /** Same launch power as the trident. */
    public static final float SHOOT_POWER = 2.5F;
    /** Same aim spread as the trident. */
    public static final float SHOOT_INACCURACY = 1.0F;

    public DreamSpearItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.SPEAR;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    /**
     * Charging a throw, or clearing the anchor that stands in the way of one.
     *
     * <p>Each layer can hold one anchor, so a spear refuses to charge while its own layer already has
     * one; the hotbar hint tells the player to sneak-use instead. Sneak-use destroys the bound spear
     * of the layer the player is on and does not start a throw.</p>
     */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                destroyBoundSpear(player);
            }
            return InteractionResult.SUCCESS;
        }
        if (hasAnchor(player)) {
            return InteractionResult.FAIL;
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    /** {@return whether this player's current layer already has an anchor} */
    private static boolean hasAnchor(Player player) {
        boolean dreamSide = isDreaming(player);
        if (player.level().isClientSide()) {
            // The server mirrors the flags to the client; the anchored spear itself may be out of view.
            return DreamClientState.isAnchorBound(dreamSide);
        }
        return player instanceof ServerPlayer serverPlayer
                && DreamManager.hasAnchor(serverPlayer, dreamSide);
    }

    /** {@return whether the player is currently on the dream layer} */
    private static boolean isDreaming(Player player) {
        if (player.level().isClientSide()) {
            return DreamClientState.isDreaming();
        }
        return player instanceof ServerPlayer serverPlayer && DreamManager.isDreaming(serverPlayer);
    }

    /** Removes the anchor of the player's current layer, freeing the slot for a new throw. */
    private static void destroyBoundSpear(Player player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        UUID owner = player.getUUID();
        boolean dreamSide = isDreaming(player);
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof DreamSpearEntity spear
                        && owner.equals(spear.owner())
                        && spear.isAnchored()
                        && spear.dreamSide() == dreamSide) {
                    spear.destroyAnchor();
                    return;
                }
            }
        }
        // Nothing anchored any more: make sure a stale record cannot block the next throw.
        if (player instanceof ServerPlayer serverPlayer) {
            DreamManager.clearAnchor(serverPlayer, dreamSide);
        }
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        int charge = this.getUseDuration(stack, entity) - timeLeft;
        if (charge < THROW_THRESHOLD_TIME) {
            return false;
        }
        if (hasThrownSpear(player)) {
            // One anchor per layer: the spear has to come back before another one may fly.
            return false;
        }
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level instanceof ServerLevel serverLevel) {
            throwSpear(serverLevel, serverPlayer, stack);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        return true;
    }

    /**
     * Spawns the thrown spear and takes the item out of the inventory.
     *
     * <p>The launch follows {@code Projectile#shootFromRotation}: the aim is the player's look vector
     * with the trident's spread, and the entity's yaw and pitch are recomputed from the perturbed
     * velocity, so the spear flies and points along the same line.</p>
     */
    private static void throwSpear(ServerLevel level, ServerPlayer player, ItemStack stack) {
        DreamSpearEntity spear = new DreamSpearEntity(DWObjects.DREAM_SPEAR_ENTITY.get(), level);
        spear.setOwner(player.getUUID());
        // The layer is fixed at throw time: a spear thrown inside the dream reads the dream terrain
        // while flying and pins a dream-side anchor, one thrown in reality does the opposite.
        spear.setDreamSide(DreamManager.isDreaming(player));
        // The dreamer is not forced into any pose, so the eye height is the ordinary standing one
        // and the spear starts at eye level, centred on the eye so it fits a two-block room.
        double eyeY = player.getEyeY();
        spear.snapTo(player.getX(), eyeY - spear.getBbHeight() * 0.5D, player.getZ(),
                player.getYRot(), player.getXRot());

        Vec3 aim = player.getLookAngle()
                .normalize()
                .add(player.getRandom().triangle(0.0D, 0.0172275D * SHOOT_INACCURACY),
                        player.getRandom().triangle(0.0D, 0.0172275D * SHOOT_INACCURACY),
                        player.getRandom().triangle(0.0D, 0.0172275D * SHOOT_INACCURACY))
                .scale(SHOOT_POWER);
        spear.setDeltaMovement(aim);
        spear.setYRot((float) (Mth.atan2(aim.x, aim.z) * (180.0D / Math.PI)));
        spear.setXRot((float) (Mth.atan2(aim.y, aim.horizontalDistance()) * (180.0D / Math.PI)));

        level.addFreshEntity(spear);
        level.playSound(null, spear.getX(), spear.getY(), spear.getZ(), SoundEvents.TRIDENT_THROW,
                SoundSource.PLAYERS, 1.0F, 1.0F);
        if (!DreamPlatform.get().hasInfiniteMaterials(player)) {
            player.getInventory().removeItem(stack);
        }
    }

    /** {@return whether the player already has a thrown spear on the current layer} */
    private static boolean hasThrownSpear(Player player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return false;
        }
        UUID owner = player.getUUID();
        boolean dreamSide = isDreaming(player);
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof DreamSpearEntity spear
                        && owner.equals(spear.owner())
                        && spear.dreamSide() == dreamSide) {
                    return true;
                }
            }
        }
        return false;
    }
}
