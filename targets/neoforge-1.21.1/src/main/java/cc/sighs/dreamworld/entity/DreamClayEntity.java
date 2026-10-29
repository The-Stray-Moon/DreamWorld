package cc.sighs.dreamworld.entity;

import cc.sighs.dreamworld.DWRegistry;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Dream clay: a floating blob spawned next to the bed when a player wakes up, or produced by a
 * dream rabbit. It drifts through the air, disappears after a while and can be collected.
 */
public class DreamClayEntity extends Entity implements ItemSupplier {
    /** Ticks before the blob dissolves. */
    public static final int LIFETIME = 20 * 120;
    /** Ticks the blob cannot be collected after spawning. */
    public static final int PICKUP_DELAY = 10;
    /** Maximum length of the drift velocity, in blocks per tick. */
    private static final double MAX_SPEED = 0.08D;
    /** Distance from the spawn point at which the blob starts drifting back. */
    private static final double HOME_RADIUS = 1.5D;

    private int age;
    /** Center the blob drifts around, kept in world space so it never flies away. */
    private Vec3 home = Vec3.ZERO;

    public DreamClayEntity(EntityType<? extends DreamClayEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /**
     * Spawns a blob floating next to {@code center}.
     *
     * @return the spawned entity, already added to the level
     */
    public static DreamClayEntity spawnNear(ServerLevel level, Vec3 center) {
        DreamClayEntity clay = new DreamClayEntity(DWRegistry.DREAM_CLAY_ENTITY.get(), level);
        double x = center.x + (level.random.nextDouble() - 0.5D) * 1.6D;
        double y = center.y + 0.6D + level.random.nextDouble() * 0.6D;
        double z = center.z + (level.random.nextDouble() - 0.5D) * 1.6D;
        clay.setPos(x, y, z);
        clay.home = new Vec3(center.x, y, center.z);
        clay.setDeltaMovement((level.random.nextDouble() - 0.5D) * 0.02D, 0.0D,
                (level.random.nextDouble() - 0.5D) * 0.02D);
        level.addFreshEntity(clay);
        return clay;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        // The drift is simulated on the server only, the client interpolates the synced position.
        if (level().isClientSide) {
            return;
        }
        if (++this.age >= LIFETIME) {
            discard();
            return;
        }
        Vec3 motion = getDeltaMovement()
                .add((random.nextDouble() - 0.5D) * 0.02D, (random.nextDouble() - 0.5D) * 0.02D,
                        (random.nextDouble() - 0.5D) * 0.02D)
                .scale(0.92D);
        Vec3 toHome = this.home.subtract(position());
        if (toHome.lengthSqr() > HOME_RADIUS * HOME_RADIUS) {
            motion = motion.add(toHome.normalize().scale(0.012D));
        }
        if (motion.lengthSqr() > MAX_SPEED * MAX_SPEED) {
            motion = motion.normalize().scale(MAX_SPEED);
        }
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
    }

    @Override
    public ItemStack getItem() {
        return new ItemStack(DWRegistry.DREAM_CLAY.get());
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player.isSpectator() || this.age < PICKUP_DELAY) {
            return InteractionResult.PASS;
        }
        giveTo(player);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) {
            return true;
        }
        if (this.age < PICKUP_DELAY || !(source.getEntity() instanceof Player player)) {
            return false;
        }
        giveTo(player);
        return true;
    }

    /** Converts the blob into its item form, dropping the item on the ground. */
    public void collect() {
        giveTo(null);
    }

    private void giveTo(@Nullable Player player) {
        if (level().isClientSide || isRemoved()) {
            return;
        }
        ItemStack stack = new ItemStack(DWRegistry.DREAM_CLAY.get());
        if (player == null) {
            spawnAtLocation(stack, 0.2F);
        } else {
            player.getInventory().placeItemBackInInventory(stack);
        }
        level().playSound(null, blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.4F, 1.4F);
        discard();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.age = tag.getInt("Age");
        if (tag.contains("HomeX")) {
            this.home = new Vec3(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ"));
        } else {
            this.home = position();
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", this.age);
        tag.putDouble("HomeX", this.home.x);
        tag.putDouble("HomeY", this.home.y);
        tag.putDouble("HomeZ", this.home.z);
    }
}
