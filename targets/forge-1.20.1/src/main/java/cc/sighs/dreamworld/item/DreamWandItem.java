package cc.sighs.dreamworld.item;

import java.util.ArrayList;
import java.util.List;
import cc.sighs.dreamworld.dream.DreamEntities;
import cc.sighs.dreamworld.dream.DreamIntrusion;
import cc.sighs.dreamworld.dream.DreamManager;
import cc.sighs.dreamworld.net.DreamPackets;
import cc.sighs.dreamworld.spi.DreamPlatform;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Dream alloy wand: charged aimed blast at the crosshair.
 *
 * <p>The item is charged by holding the use button; releasing it raycasts along the player's look
 * vector and blasts the first block hit (or the end of the ray). The longer the charge, the bigger
 * the radius (1 to 4) and the harder the hit. The blast damages creatures on the layer it happened
 * on - never through the mirror - and the blocks inside the radius are handed over to the dream
 * intrusion instead of being destroyed by an explosion, so no loot and no experience are spawned.
 * A lethal blast carries its victim across the mirror exactly like a sword kill.</p>
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
    /** Damage at the edge of a fully charged blast and of a minimum one. */
    private static final float MIN_DAMAGE = 6.0F;
    private static final float MAX_DAMAGE = 18.0F;
    /** Weakest damage factor at the outer edge of the blast. */
    private static final double MIN_FALLOFF = 0.25D;

    public DreamWandItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
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
        int charge = this.getUseDuration(stack) - timeLeft;
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
        damageCreatures(player, serverLevel, target, radius, progress);

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

        stack.hurtAndBreak(1, player, broken -> broken.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
    }

    /**
     * Damages the creatures caught in the blast, on the layer the blast happened on.
     *
     * <p>The two layers share one server world, so a dream blast would otherwise also wound the real
     * creatures standing at the same coordinates - creatures the dreamer cannot even see. Only
     * creatures belonging to the caster's layer are hit, which is also what keeps the intrusion that
     * follows a kill on the right side of the mirror.</p>
     *
     * <p>The damage is an explosion caused by the player, so it flows through the normal hit event
     * and a lethal blast is turned into a dream intrusion like a sword kill.</p>
     */
    private static void damageCreatures(ServerPlayer player, ServerLevel level, Vec3 center,
            float radius, float chargeProgress) {
        boolean dreamSide = DreamManager.isDreaming(player);
        float base = Mth.lerp(chargeProgress, MIN_DAMAGE, MAX_DAMAGE);
        DamageSource source = level.damageSources().explosion(player, player);
        for (Entity entity : level.getEntities(player, new AABB(center, center).inflate(radius))) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
                continue;
            }
            if (DreamEntities.isDreamOnly(living) != dreamSide) {
                continue;
            }
            double distance = Math.sqrt(living.getBoundingBox().getCenter().distanceToSqr(center));
            float falloff = (float) Mth.clamp(1.0D - distance / (radius + 1.0D), MIN_FALLOFF, 1.0D);
            living.hurt(source, base * falloff);
        }
    }

    /**
     * Plays the sound and the particles of a vanilla blast without creating an explosion: the blocks
     * are handed to the intrusion instead of being destroyed, so no loot and no experience spawn.
     *
     * <p>The blast is finished with a burst of cherry petals that flies outwards from the centre.
     * Vanilla's particle packet cannot express that - it gives every particle a random velocity
     * unrelated to its spawn offset - so the burst is sent as its own message and spawned by each
     * client radially.</p>
     */
    private static void playBlastEffects(ServerLevel level, Vec3 target, float radius) {
        level.playSound(null, target.x, target.y, target.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS,
                4.0F, (1.0F + (level.random.nextFloat() - level.random.nextFloat()) * 0.2F) * 0.7F);
        ParticleOptions particle = radius < 2.0F ? ParticleTypes.EXPLOSION : ParticleTypes.EXPLOSION_EMITTER;
        level.sendParticles(particle, target.x, target.y, target.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        DreamPlatform.get().network().sendToNear(level, target, radius + PARTICLE_RANGE,
                new DreamPackets.BlastParticles(target.x, target.y, target.z, radius,
                        PETALS_PER_RADIUS * Mth.ceil(radius)));
    }
}
