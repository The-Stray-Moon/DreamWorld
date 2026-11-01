package cc.sighs.dreamworld.item;

import cc.sighs.dreamworld.DWObjects;
import cc.sighs.dreamworld.client.DreamClientState;
import cc.sighs.dreamworld.dream.DreamManager;
import cc.sighs.dreamworld.entity.DreamSpearEntity;
import cc.sighs.dreamworld.spi.DreamPlatform;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Dream alloy spear: a thrown anchor with no attack damage, permanently bound to one thrower.
 *
 * <p>Its use is the trident's, minus the enchantment paths: hold to charge, release to throw, and
 * the charge has to last {@link #THROW_THRESHOLD_TIME} ticks. The spear is spawned with the same
 * launch speed and aim spread as a trident, plays the same throw sound, is removed from the inventory
 * on the server unless the thrower has infinite materials, and the use is counted in the item-used
 * statistic on both sides. A single player may only have one spear in the world at a time; the owner
 * has to pick it up again before throwing another one.</p>
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

    /** Same as the trident: only creative throws may pass through blocks. */
    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return !player.isCreative();
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public int getEnchantmentValue() {
        return 1;
    }

    /**
     * Charging a throw, or clearing the anchor that stands in the way of one.
     *
     * <p>A player can only have one anchor at a time, so a spear with an anchor already bound refuses
     * to charge; the hotbar hint tells the player to sneak-use instead. Sneak-use destroys the bound
     * spear on the server and does not start a throw.</p>
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                destroyBoundSpear(player);
            }
            return InteractionResultHolder.success(stack);
        }
        if (hasAnchor(player)) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    /** {@return whether an anchor is already bound to this player} */
    private static boolean hasAnchor(Player player) {
        if (player.level.isClientSide) {
            // The server mirrors the flag to the client; the anchored spear itself may be out of view.
            return DreamClientState.isAnchorBound();
        }
        return player instanceof ServerPlayer serverPlayer && DreamManager.hasAnchor(serverPlayer);
    }

    /** Removes the spear the player has anchored, freeing the slot for a new throw. */
    private static void destroyBoundSpear(Player player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        UUID owner = player.getUUID();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getEntities().getAll()) {
                if (entity instanceof DreamSpearEntity spear
                        && owner.equals(spear.owner())
                        && spear.isAnchored()) {
                    spear.destroyAnchor();
                    return;
                }
            }
        }
        // Nothing anchored any more: make sure a stale record cannot block the next throw.
        if (player instanceof ServerPlayer serverPlayer) {
            DreamManager.clearAnchor(serverPlayer);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) {
            return;
        }
        int charge = this.getUseDuration(stack) - timeLeft;
        if (charge < THROW_THRESHOLD_TIME) {
            return;
        }
        if (hasThrownSpear(player)) {
            // One anchor per player: the spear has to come back before another one may fly.
            return;
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level instanceof ServerLevel serverLevel) {
            throwSpear(serverLevel, serverPlayer, stack);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
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
        spear.moveTo(player.getX(), eyeY - spear.getBbHeight() * 0.5D, player.getZ(),
                player.getYRot(), player.getXRot());

        // 1.18.2 has no triangle distribution; the gaussian is the same zero-mean spread.
        double spread = 0.0172275D * SHOOT_INACCURACY;
        Vec3 aim = player.getLookAngle()
                .normalize()
                .add(player.getRandom().nextGaussian() * spread,
                        player.getRandom().nextGaussian() * spread,
                        player.getRandom().nextGaussian() * spread)
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

    /** {@return whether the player already has a thrown spear anywhere in the world} */
    private static boolean hasThrownSpear(Player player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        UUID owner = player.getUUID();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getEntities().getAll()) {
                if (entity instanceof DreamSpearEntity spear && owner.equals(spear.owner())) {
                    return true;
                }
            }
        }
        return false;
    }
}
