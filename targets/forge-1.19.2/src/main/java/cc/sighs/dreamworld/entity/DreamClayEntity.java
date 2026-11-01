package cc.sighs.dreamworld.entity;

import cc.sighs.dreamworld.DWObjects;
import javax.annotation.Nullable;
import cc.sighs.dreamworld.dream.DreamManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
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
 *
 * <p>The drift is simulated on the server only and is always smooth: the blob picks a slow random
 * target velocity every few ticks and steers towards it with a capped acceleration instead of
 * being kicked around every tick. Clients never simulate the drift themselves, they ease towards
 * the authoritative server position, so the view is smooth even though the network only delivers
 * one position per tick.</p>
 */
public class DreamClayEntity extends Entity implements ItemSupplier {
    /** Ticks before the blob dissolves. */
    public static final int LIFETIME = 20 * 120;
    /** Ticks the blob cannot be collected after spawning. */
    public static final int PICKUP_DELAY = 10;
    /** Hard cap for the drift velocity, in blocks per tick. */
    private static final double MAX_SPEED = 0.06D;
    /** Length of a randomly picked drift velocity, in blocks per tick. */
    private static final double DRIFT_SPEED = 0.022D;
    /** Ticks between two drift direction changes. */
    private static final int RETARGET_INTERVAL = 30;
    /** Largest velocity change of one tick, in blocks per tick. */
    private static final double MAX_ACCELERATION = 0.0035D;
    /** Distance from the center at which the blob starts drifting back. */
    private static final double HOME_RADIUS = 1.5D;
    /** Spring strength of the pull back to the center, in blocks per tick per block of excess distance. */
    private static final double HOME_PULL = 0.05D;
    /** Fraction of the remaining distance a client closes per tick while easing to the server. */
    private static final double CLIENT_LERP = 0.5D;
    /** Distance in blocks above which the client snaps instead of easing (server teleport). */
    private static final double CLIENT_SNAP_DISTANCE = 8.0D;
    /** Distance below which the client is close enough to take the server position as is. */
    private static final double CLIENT_SNAP_SQR = 1.0E-6D;

    private static final EntityDataAccessor<Boolean> DATA_DREAM_ORIGIN =
            SynchedEntityData.defineId(DreamClayEntity.class, EntityDataSerializers.BOOLEAN);

    private int age;
    /** True when the blob belongs to the dream layer and must stay invisible to awake players. */
    private boolean dreamOrigin;
    /** Center the blob drifts around, kept in world space so it never flies away. */
    private Vec3 home = Vec3.ZERO;
    /** Velocity the blob steers towards; only meaningful on the server. */
    private Vec3 drift = Vec3.ZERO;
    private int retargetTimer;
    /** Position the server put the blob at, eased towards on the client. */
    @Nullable
    private Vec3 lerpTarget;

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
        return spawnNear(level, center, false);
    }

    /**
     * Spawns a blob on the given layer.
     *
     * @param dreamOrigin true when the blob belongs to the dream layer
     */
    public static DreamClayEntity spawnNear(ServerLevel level, Vec3 center, boolean dreamOrigin) {
        DreamClayEntity clay = new DreamClayEntity(DWObjects.DREAM_CLAY_ENTITY.get(), level);
        clay.dreamOrigin = dreamOrigin;
        clay.getEntityData().set(DATA_DREAM_ORIGIN, dreamOrigin);
        double x = center.x + (level.random.nextDouble() - 0.5D) * 1.6D;
        double y = center.y + 0.6D + level.random.nextDouble() * 0.6D;
        double z = center.z + (level.random.nextDouble() - 0.5D) * 1.6D;
        clay.setPos(x, y, z);
        clay.home = new Vec3(center.x, y, center.z);
        clay.drift = pickDrift(level.random);
        clay.setDeltaMovement(clay.drift);
        level.addFreshEntity(clay);
        return clay;
    }

    /** Picks a random drift velocity the blob steers towards from now on. */
    private static Vec3 pickDrift(RandomSource random) {
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double vertical = (random.nextDouble() - 0.5D) * 0.6D;
        return new Vec3(Math.cos(angle) * DRIFT_SPEED, vertical * DRIFT_SPEED, Math.sin(angle) * DRIFT_SPEED);
    }

    @Override
    public Packet<?> getAddEntityPacket() {
        return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_DREAM_ORIGIN, false);
    }

    /** True when the blob lives on the dream layer. */
    public boolean isDreamOrigin() {
        return this.getEntityData().get(DATA_DREAM_ORIGIN);
    }

    /** A dream blob is only sent to dreaming players, an awake blob only to awake players. */
    @Override
    public boolean broadcastToPlayer(ServerPlayer player) {
        return isDreamOrigin() == DreamManager.isDreaming(player);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level.isClientSide) {
            this.easeToServerPosition();
            return;
        }
        if (++this.age >= LIFETIME) {
            discard();
            return;
        }
        if (++this.retargetTimer >= RETARGET_INTERVAL) {
            this.retargetTimer = 0;
            this.drift = pickDrift(this.random);
        }
        Vec3 desired = this.drift;
        Vec3 toHome = this.home.subtract(position());
        double distance = toHome.length();
        if (distance > HOME_RADIUS) {
            double pull = Math.min(1.0D, (distance - HOME_RADIUS) / HOME_RADIUS) * HOME_PULL;
            desired = desired.add(toHome.scale(pull / distance));
        }
        if (desired.lengthSqr() > MAX_SPEED * MAX_SPEED) {
            desired = desired.normalize().scale(MAX_SPEED);
        }
        // Steer towards the wanted velocity instead of snapping to it: the acceleration of a single
        // tick is capped, which keeps the curve smooth both on the server and on the clients.
        Vec3 motion = getDeltaMovement();
        Vec3 correction = desired.subtract(motion);
        double correctionLength = correction.length();
        if (correctionLength > 1.0E-5D) {
            motion = motion.add(correction.scale(Math.min(correctionLength, MAX_ACCELERATION) / correctionLength));
        }
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
    }

    /**
     * Takes the position packet from the server as a target and closes a fixed share of the
     * remaining distance every tick, instead of jumping to the position the packet carried.
     */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps,
            boolean interpolation) {
        boolean farAway = this.position().distanceToSqr(x, y, z) > CLIENT_SNAP_DISTANCE * CLIENT_SNAP_DISTANCE;
        if (!this.level.isClientSide || farAway) {
            this.lerpTarget = null;
            super.lerpTo(x, y, z, yRot, xRot, steps, interpolation);
            return;
        }
        this.lerpTarget = new Vec3(x, y, z);
        this.setRot(yRot, xRot);
    }

    private void easeToServerPosition() {
        Vec3 target = this.lerpTarget;
        if (target == null) {
            return;
        }
        Vec3 step = target.subtract(this.position()).scale(CLIENT_LERP);
        if (step.lengthSqr() < CLIENT_SNAP_SQR) {
            this.setPos(target.x, target.y, target.z);
            this.lerpTarget = null;
        } else {
            this.setPos(this.getX() + step.x, this.getY() + step.y, this.getZ() + step.z);
        }
    }

    @Override
    public ItemStack getItem() {
        return new ItemStack(DWObjects.DREAM_CLAY.get());
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level.isClientSide) {
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
        if (level.isClientSide) {
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
        if (level.isClientSide || isRemoved()) {
            return;
        }
        ItemStack stack = new ItemStack(DWObjects.DREAM_CLAY.get());
        if (player == null) {
            spawnAtLocation(stack, 0.2F);
        } else {
            player.getInventory().placeItemBackInInventory(stack);
        }
        level.playSound(null, blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.4F, 1.4F);
        discard();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.age = tag.getInt("Age");
        this.getEntityData().set(DATA_DREAM_ORIGIN, tag.getBoolean("DreamOrigin"));
        this.dreamOrigin = tag.getBoolean("DreamOrigin");
        if (tag.contains("HomeX")) {
            this.home = new Vec3(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ"));
        } else {
            this.home = position();
        }
        // Continue the drift that was saved with the blob instead of stopping for a retarget.
        this.drift = getDeltaMovement();
        if (this.drift.lengthSqr() > MAX_SPEED * MAX_SPEED) {
            this.drift = this.drift.normalize().scale(MAX_SPEED);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", this.age);
        tag.putDouble("HomeX", this.home.x);
        tag.putDouble("HomeY", this.home.y);
        tag.putDouble("HomeZ", this.home.z);
        tag.putBoolean("DreamOrigin", isDreamOrigin());
    }
}
