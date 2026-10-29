package cc.sighs.dreamworld.item;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Dream alloy wand: charged aimed explosion at the crosshair.
 *
 * <p>The item is charged by holding the use button; releasing it raycasts along the player's look
 * vector and detonates an explosion at the first block hit (or at the end of the ray). The longer
 * the charge, the bigger the radius (1 to 4). Explosions are attributed to the player and the
 * destroyed blocks trigger a dream intrusion while the player is dreaming.</p>
 */
public class DreamWandItem extends Item {
    /** Ticks of charging needed for the maximum radius. */
    public static final int MAX_CHARGE_TICKS = 40;
    public static final float MIN_RADIUS = 1.0F;
    public static final float MAX_RADIUS = 4.0F;
    /** How far the crosshair ray reaches, in blocks. */
    public static final double RANGE = 32.0D;
    public static final int COOLDOWN_TICKS = 30;

    public DreamWandItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)
                || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (player.getCooldowns().isOnCooldown(this)) {
            return;
        }
        int charge = this.getUseDuration(stack, player) - timeLeft;
        float progress = Mth.clamp((float) charge / (float) MAX_CHARGE_TICKS, 0.0F, 1.0F);
        float radius = Mth.lerp(progress, MIN_RADIUS, MAX_RADIUS);

        Vec3 from = player.getEyePosition(1.0F);
        Vec3 to = from.add(player.getLookAngle().scale(RANGE));
        BlockHitResult hit = level.clip(
                new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 target = hit.getLocation();

        // Damage only: blocks are never blown up for real, they are transferred by the intrusion
        // path below without loot or experience.
        level.explode(player, target.x, target.y, target.z, radius, Level.ExplosionInteraction.NONE);

        int reach = Mth.ceil(radius);
        BlockPos center = BlockPos.containing(target);
        List<BlockPos> caught = new java.util.ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-reach, -reach, -reach),
                center.offset(reach, reach, reach))) {
            if (pos.distSqr(center) <= (double) radius * radius) {
                caught.add(pos.immutable());
            }
        }
        DreamToolEvents.handleBlocksBroken(player, serverLevel, caught);

        stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
    }
}
