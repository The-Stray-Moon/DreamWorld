package cc.sighs.dreamworld.item;

import java.util.ArrayList;
import java.util.List;
import cc.sighs.dreamworld.dream.DreamIntrusion;
import cc.sighs.dreamworld.dream.DreamManager;
import cc.sighs.dreamworld.net.DWPayloads;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Dream alloy wand: charged aimed blast at the crosshair.
 *
 * <p>The item is charged by holding the use button; releasing it raycasts along the player's look
 * vector and blasts the first block hit (or the end of the ray). The longer the charge, the bigger
 * the radius (1 to 4). The blast never damages anything living: it only plays the vanilla explosion
 * sound and particles, and the blocks inside the radius are handed over to the dream intrusion
 * instead of being destroyed by an explosion, so no loot and no experience are spawned.</p>
 */
public class DreamWandItem extends Item {
    /** Ticks of charging needed for the maximum radius. */
    public static final int MAX_CHARGE_TICKS = 40;
    public static final float MIN_RADIUS = 1.0F;
    public static final float MAX_RADIUS = 4.0F;
    /** How far the crosshair ray reaches, in blocks. */
    public static final double RANGE = 32.0D;
    public static final int COOLDOWN_TICKS = 30;
    /** Cherry petals spawned per block of blast radius. */
    private static final int PETALS_PER_RADIUS = 12;
    /** How far past the blast the petal packet is sent, in blocks. */
    private static final double PARTICLE_RANGE = 32.0D;

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
        // The dream layer is not the real one: clipping against the server's chunks would put the
        // blast where the crosshair is not, or send it flying to the end of the ray.
        BlockHitResult hit = DreamManager.isDreaming(player)
                ? DreamIntrusion.clipDream(serverLevel, from, to)
                : level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, player));
        Vec3 target = hit.getLocation();

        playBlastEffects(serverLevel, target, radius);

        int reach = Mth.ceil(radius);
        BlockPos center = BlockPos.containing(target);
        List<BlockPos> caught = new ArrayList<>();
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

    /**
     * Plays the sound and the particles of a vanilla blast without creating an explosion: no entity
     * is ever hurt and no block is destroyed by the effect itself.
     *
     * <p>The blast is finished with a burst of cherry petals that flies outwards from the centre.
     * Vanilla's particle packet cannot express that - it gives every particle a random velocity
     * unrelated to its spawn offset - so the burst is sent as its own payload and spawned by each
     * client radially.</p>
     */
    private static void playBlastEffects(ServerLevel level, Vec3 target, float radius) {
        level.playSound(null, target.x, target.y, target.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS,
                4.0F, (1.0F + (level.random.nextFloat() - level.random.nextFloat()) * 0.2F) * 0.7F);
        ParticleOptions particle = radius < 2.0F ? ParticleTypes.EXPLOSION : ParticleTypes.EXPLOSION_EMITTER;
        level.sendParticles(particle, target.x, target.y, target.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        PacketDistributor.sendToPlayersNear(level, null, target.x, target.y, target.z,
                radius + PARTICLE_RANGE,
                new DWPayloads.BlastParticlesPayload(target.x, target.y, target.z, radius,
                        PETALS_PER_RADIUS * Mth.ceil(radius)));
    }
}
