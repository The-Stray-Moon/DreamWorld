package cc.sighs.dreamworld.entity;

import cc.sighs.dreamworld.DWRegistry;
import cc.sighs.dreamworld.dream.DreamManager;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Thrown dream alloy spear. It deals no damage, pins itself in place once it hits something,
 * force-loads its chunk, is bound to its thrower and acts as a dream anchor until picked up.
 */
public class DreamSpearEntity extends Entity implements ItemSupplier {
    /** Downward acceleration applied while the spear is still flying. */
    private static final double GRAVITY = 0.04D;
    /** Horizontal drag applied while the spear is still flying. */
    private static final double DRAG = 0.99D;
    /**
     * Number of spears currently forcing a chunk, per dimension and chunk. Vanilla forced chunks
     * are a plain set, so the count keeps two spears in the same chunk from releasing it early.
     */
    private static final Map<ResourceKey<Level>, Map<Long, Integer>> FORCED_CHUNKS = new HashMap<>();

    @Nullable
    private UUID owner;
    @Nullable
    private UUID anchoredEntity;
    private boolean anchored;
    private boolean chunkForced;
    @Nullable
    private ChunkPos forcedChunk;

    public DreamSpearEntity(EntityType<? extends DreamSpearEntity> type, Level level) {
        super(type, level);
        this.setInvulnerable(true);
        this.setNoGravity(true);
    }

    /**
     * Creates a spear owned by {@code thrower}, aimed along its view and added to the level.
     */
    public static DreamSpearEntity spawnThrown(ServerLevel level, Player thrower) {
        DreamSpearEntity spear = new DreamSpearEntity(DWRegistry.DREAM_SPEAR_ENTITY.get(), level);
        spear.setOwner(thrower.getUUID());
        Vec3 direction = thrower.getViewVector(1.0F);
        spear.setPos(thrower.getEyePosition().add(direction.scale(0.5D)).subtract(0.0D, 0.2D, 0.0D));
        spear.throwTowards(direction, 1.8F);
        level.addFreshEntity(spear);
        return spear;
    }

    /** Sets the initial velocity of a freshly created spear. */
    public void throwTowards(Vec3 direction, float speed) {
        Vec3 velocity = direction.normalize().scale(speed);
        setDeltaMovement(velocity);
        setYRot((float) (Mth.atan2(-velocity.x, velocity.z) * (180.0D / Math.PI)));
        setXRot((float) (-Mth.atan2(velocity.y, Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z))
                * (180.0D / Math.PI)));
        this.hasImpulse = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        if (!level().isClientSide && getY() < level().getMinBuildHeight() - 32.0D) {
            // Fell out of the world: never leave the chunk forced behind.
            releaseForcedChunk();
            discard();
            return;
        }
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (this.anchored) {
            setDeltaMovement(Vec3.ZERO);
            if (!this.chunkForced) {
                forceCurrentChunk();
            }
            return;
        }
        Vec3 motion = getDeltaMovement();
        Vec3 next = new Vec3(motion.x * DRAG, motion.y - GRAVITY, motion.z * DRAG);
        setDeltaMovement(next);
        move(MoverType.SELF, next);
        if (this.horizontalCollision || this.verticalCollision || onGround()) {
            anchor();
            return;
        }
        Entity hit = findEntityHit();
        if (hit != null) {
            this.anchoredEntity = hit.getUUID();
            anchor();
        }
    }

    /** Pins the spear where it currently is, force-loads that chunk and binds it as the anchor. */
    public void anchor() {
        setAnchored(true);
        syncAnchorToOwner(this.owner, true);
    }

    /**
     * Binds or clears this spear as the thrower's dream anchor, so intrusions performed while
     * dreaming land on the spear instead of the dreamer's own coordinates.
     */
    private void syncAnchorToOwner(@Nullable java.util.UUID ownerId, boolean bound) {
        if (!(level() instanceof ServerLevel serverLevel) || ownerId == null) {
            return;
        }
        ServerPlayer ownerPlayer = serverLevel.getServer().getPlayerList().getPlayer(ownerId);
        if (ownerPlayer == null) {
            return;
        }
        if (!bound) {
            DreamManager.clearAnchor(ownerPlayer);
        } else if (this.anchoredEntity != null) {
            DreamManager.setEntityAnchor(ownerPlayer, this.anchoredEntity);
        } else {
            DreamManager.setBlockAnchor(ownerPlayer, serverLevel.dimension(), blockPosition());
        }
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
        return pickUp(player) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    /** Picks the spear back up for its owner. */
    public boolean pickUp(Player player) {
        if (level().isClientSide() || !this.anchored || player.isSpectator()) {
            return false;
        }
        if (this.owner != null && !this.owner.equals(player.getUUID())) {
            return false;
        }
        player.getInventory().placeItemBackInInventory(new ItemStack(DWRegistry.DREAM_ALLOY_SPEAR.get()));
        syncAnchorToOwner(this.owner, false);
        releaseForcedChunk();
        discard();
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public ItemStack getItem() {
        return new ItemStack(DWRegistry.DREAM_ALLOY_SPEAR.get());
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(DWRegistry.DREAM_ALLOY_SPEAR.get());
    }

    @Nullable
    public UUID owner() {
        return this.owner;
    }

    /** Owner UUID wrapped in an {@link Optional}, kept for callers that prefer it. */
    public Optional<UUID> ownerId() {
        return Optional.ofNullable(this.owner);
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
    }

    /** UUID of the entity the spear stuck into, if any. */
    @Nullable
    public UUID anchoredEntity() {
        return this.anchoredEntity;
    }

    public boolean isAnchored() {
        return this.anchored;
    }

    public void setAnchored(boolean anchored) {
        if (this.anchored == anchored) {
            return;
        }
        this.anchored = anchored;
        this.noPhysics = anchored;
        this.setNoGravity(anchored);
        if (anchored) {
            setDeltaMovement(Vec3.ZERO);
            setOldPosAndRot();
            this.hasImpulse = true;
            forceCurrentChunk();
        } else {
            releaseForcedChunk();
        }
    }

    @Nullable
    private Entity findEntityHit() {
        AABB box = getBoundingBox().inflate(0.3D);
        for (Entity entity : level().getEntities(this, box)) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isSpectator()) {
                continue;
            }
            if (this.owner != null && this.owner.equals(living.getUUID())) {
                continue;
            }
            return living;
        }
        return null;
    }

    private void forceCurrentChunk() {
        if (level() instanceof ServerLevel server) {
            ChunkPos pos = new ChunkPos(blockPosition());
            forceChunk(server, pos);
            this.forcedChunk = pos;
            this.chunkForced = true;
        }
    }

    private void releaseForcedChunk() {
        if (this.chunkForced && this.forcedChunk != null && level() instanceof ServerLevel server) {
            releaseChunk(server, this.forcedChunk);
        }
        this.chunkForced = false;
        this.forcedChunk = null;
    }

    private static void forceChunk(ServerLevel level, ChunkPos pos) {
        Map<Long, Integer> counts = FORCED_CHUNKS.computeIfAbsent(level.dimension(), key -> new HashMap<>());
        int count = counts.merge(pos.toLong(), 1, Integer::sum);
        if (count == 1) {
            level.setChunkForced(pos.x, pos.z, true);
        }
    }

    private static void releaseChunk(ServerLevel level, ChunkPos pos) {
        Map<Long, Integer> counts = FORCED_CHUNKS.get(level.dimension());
        if (counts == null) {
            return;
        }
        Integer count = counts.get(pos.toLong());
        if (count == null) {
            return;
        }
        if (count <= 1) {
            counts.remove(pos.toLong());
            level.setChunkForced(pos.x, pos.z, false);
        } else {
            counts.put(pos.toLong(), count - 1);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) {
            this.owner = tag.getUUID("Owner");
        }
        if (tag.hasUUID("AnchoredEntity")) {
            this.anchoredEntity = tag.getUUID("AnchoredEntity");
        }
        this.anchored = tag.getBoolean("Anchored");
        this.noPhysics = this.anchored;
        this.setNoGravity(this.anchored);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.owner != null) {
            tag.putUUID("Owner", this.owner);
        }
        if (this.anchoredEntity != null) {
            tag.putUUID("AnchoredEntity", this.anchoredEntity);
        }
        tag.putBoolean("Anchored", this.anchored);
    }
}
