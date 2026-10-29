package cc.sighs.dreamworld.item;

import cc.sighs.dreamworld.DWRegistry;
import cc.sighs.dreamworld.entity.DreamSpearEntity;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.phys.Vec3;

/**
 * Dream alloy spear: throwable anchor, no attack damage, permanently bound to one thrower.
 *
 * <p>Throwing mirrors the trident: the spear has to be charged for a few ticks, is then spawned
 * with the thrower's aim and removed from the inventory. A single player may only have one spear
 * in the world at a time; the owner has to pick it up again before throwing another one.</p>
 */
public class DreamSpearItem extends Item {
    /** Same charge threshold as the trident. */
    public static final int THROW_THRESHOLD_TIME = 10;
    /** Same launch power as the trident. */
    public static final float SHOOT_POWER = 2.5F;
    public static final float SHOOT_INACCURACY = 1.0F;

    public DreamSpearItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)
                || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        int charge = this.getUseDuration(stack, player) - timeLeft;
        if (charge < THROW_THRESHOLD_TIME) {
            return;
        }
        if (hasThrownSpear(player)) {
            return;
        }

        DreamSpearEntity spear = new DreamSpearEntity(DWRegistry.DREAM_SPEAR_ENTITY.get(), serverLevel);
        spear.setOwner(player.getUUID());
        spear.moveTo(player.getX(), player.getEyeY() - 0.1D, player.getZ(), player.getYRot(), player.getXRot());

        // Entity has no shoot() helper (only Projectile does), so replicate its spread math here.
        Vec3 aim = player.getLookAngle()
                .normalize()
                .add(player.getRandom().triangle(0.0D, 0.0172275D * SHOOT_INACCURACY),
                        player.getRandom().triangle(0.0D, 0.0172275D * SHOOT_INACCURACY),
                        player.getRandom().triangle(0.0D, 0.0172275D * SHOOT_INACCURACY))
                .scale(SHOOT_POWER);
        spear.setDeltaMovement(aim);
        spear.setYRot((float) (Mth.atan2(aim.x, aim.z) * (180.0D / Math.PI)));
        spear.setXRot((float) (Mth.atan2(aim.y, aim.horizontalDistance()) * (180.0D / Math.PI)));

        serverLevel.addFreshEntity(spear);
        serverLevel.playSound(null, spear.getX(), spear.getY(), spear.getZ(), SoundEvents.TRIDENT_THROW,
                SoundSource.PLAYERS, 1.0F, 1.0F);
        if (!player.hasInfiniteMaterials()) {
            player.getInventory().removeItem(stack);
        }
    }

    /** {@return whether the player already has a thrown spear anywhere in the world} */
    private static boolean hasThrownSpear(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        UUID owner = player.getUUID();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof DreamSpearEntity spear && owner.equals(spear.owner())) {
                    return true;
                }
            }
        }
        return false;
    }
}
