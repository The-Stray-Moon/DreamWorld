package cc.sighs.dreamworld.entity;

import cc.sighs.dreamworld.DWObjects;
import cc.sighs.dreamworld.dream.DreamEntities;
import cc.sighs.dreamworld.dream.DreamManager;
import cc.sighs.dreamworld.dream.DreamPatches;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Thrown dream alloy spear. It deals no damage, pins itself in place once it hits something,
 * force-loads its chunk, is bound to its thrower and acts as a dream anchor until picked up.
 */
public class DreamSpearEntity extends Entity implements ItemSupplier {
    /** Downward acceleration applied while the spear is still flying. */
    private static final double GRAVITY = 0.04D;
    /** Horizontal drag applied while the spear is still flying. */
    private static final double DRAG = 0.99D;
    /** Step length of the dream-terrain sweep, in blocks. */
    private static final double SWEEP_STEP = 0.25D;
    /**
     * Number of spears currently forcing a chunk, per dimension and chunk. Vanilla forced chunks
     * are a plain set, so the count keeps two spears in the same chunk from releasing it early.
     */
    private static final Map<ResourceKey<Level>, Map<Long, Integer>> FORCED_CHUNKS = new HashMap<>();

    /**
     * Layer the spear was thrown on. The spear itself is a real server entity, but a spear thrown
     * inside the dream has to read the dream terrain while flying and has to pin a dream-side
     * anchor, otherwise it would sail through the dream floor or land on a real block nobody sees.
     *
     * <p>Synced: the client needs it to tell an anchor on its own layer from one on the other one,
     * which is the difference between a solid spear and a pale ghost of it.</p>
     */
    private static final EntityDataAccessor<Boolean> DATA_DREAM_SIDE =
            SynchedEntityData.defineId(DreamSpearEntity.class, EntityDataSerializers.BOOLEAN);

    /**
     * Whether the spear has pinned itself. Synced so the client can stop the flight trail and the
     * rendering of a moving projectile once the anchor is set.
     */
    private static final EntityDataAccessor<Boolean> DATA_ANCHORED =
            SynchedEntityData.defineId(DreamSpearEntity.class, EntityDataSerializers.BOOLEAN);

    /**
     * Entity id of the creature the spear is stuck in, and the offset it sits at on that body.
     *
     * <p>Both are synced because the client has to ride the creature itself: waiting for the server
     * position would make the spear trail behind the body by a round trip, which reads as stutter.
     * An id of {@code -1} means the spear is anchored to the world instead.</p>
     */
    private static final EntityDataAccessor<Integer> DATA_HOST_ID =
            SynchedEntityData.defineId(DreamSpearEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_OFFSET_X =
            SynchedEntityData.defineId(DreamSpearEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_OFFSET_Y =
            SynchedEntityData.defineId(DreamSpearEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_OFFSET_Z =
            SynchedEntityData.defineId(DreamSpearEntity.class, EntityDataSerializers.FLOAT);

    @Nullable
    private UUID owner;
    @Nullable
    private UUID anchoredEntity;
    /** Where on the anchored creature the spear sits, so it rides along with it. */
    @Nullable
    private Vec3 stickOffset;
    /** Previous client-side position, used to lay the trail along the travelled path. */
    @Nullable
    private Vec3 trailFrom;
    private boolean chunkForced;
    @Nullable
    private ChunkPos forcedChunk;

    public DreamSpearEntity(EntityType<? extends DreamSpearEntity> type, Level level) {
        super(type, level);
        this.setInvulnerable(true);
        this.setNoGravity(true);
    }

    @Override
    public Packet<?> getAddEntityPacket() {
        return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_DREAM_SIDE, false);
        this.entityData.define(DATA_ANCHORED, false);
        this.entityData.define(DATA_HOST_ID, -1);
        this.entityData.define(DATA_OFFSET_X, 0.0F);
        this.entityData.define(DATA_OFFSET_Y, 0.0F);
        this.entityData.define(DATA_OFFSET_Z, 0.0F);
    }

    @Override
    public void tick() {
        if (!level.isClientSide && getY() < level.getMinBuildHeight() - 32.0D) {
            // Fell out of the world: never leave the chunk forced behind.
            releaseForcedChunk();
            discard();
            return;
        }
        super.tick();
        if (level.isClientSide) {
            // The client does not simulate the flight, it only decorates it. Riding the host creature
            // is handled by DreamSpearFollowMixin, so this class stays free of client-only logic.
            spawnFlightTrail();
            return;
        }
        if (isAnchored()) {
            setDeltaMovement(Vec3.ZERO);
            followAnchoredEntity();
            ensureForcedChunk();
            return;
        }
        Vec3 motion = getDeltaMovement();
        Vec3 next = new Vec3(motion.x * DRAG, motion.y - GRAVITY, motion.z * DRAG);
        setDeltaMovement(next);

        // Creatures have to be tested along the whole step, not at its end: the spear covers two and
        // a half blocks per tick, so anything it passes between two ticks would be missed entirely.
        // The search is bounded by the first solid block so a creature behind a wall is not picked.
        Vec3 from = position();
        Vec3 reach = clipToTerrain(from, from.add(next));
        Entity target = findEntityAlong(from, reach);
        if (target != null) {
            stickInto(target, from, reach);
            return;
        }

        boolean blocked = dreamSide() ? moveOverDreamTerrain(next) : moveNaturally(next);
        if (blocked) {
            anchor();
        }
    }

    /** Vanilla movement against the real chunks, used by a spear thrown in reality. */
    private boolean moveNaturally(Vec3 motion) {
        move(MoverType.SELF, motion);
        return this.horizontalCollision || this.verticalCollision || isOnGround();
    }

    /** How far the step gets before a solid block stops it, on the layer the spear belongs to. */
    private Vec3 clipToTerrain(Vec3 from, Vec3 to) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return to;
        }
        if (!dreamSide()) {
            BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, this));
            return hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
        }
        Vec3 direction = to.subtract(from);
        int steps = Math.max(1, Mth.ceil(direction.length() / SWEEP_STEP));
        for (int step = 1; step <= steps; step++) {
            Vec3 point = from.add(direction.scale((double) step / steps));
            if (dreamworld$collides(serverLevel, point)) {
                return from.add(direction.scale((double) (step - 1) / steps));
            }
        }
        return to;
    }

    /**
     * First creature on the layer of this spear whose box the step passes through.
     *
     * <p>The layers share one server world, so a dream spear must ignore the real creatures a dreamer
     * cannot see, and a real spear must ignore the dream copies.</p>
     */
    @Nullable
    private Entity findEntityAlong(Vec3 from, Vec3 to) {
        AABB sweep = getBoundingBox().expandTowards(to.subtract(from)).inflate(0.3D);
        Entity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Entity entity : level.getEntities(this, sweep)) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isSpectator()) {
                continue;
            }
            if (this.owner != null && this.owner.equals(living.getUUID())) {
                continue;
            }
            if (DreamEntities.isDreamOnly(living) != dreamSide()) {
                continue;
            }
            if (living.getBoundingBox().inflate(0.3D).clip(from, to).isEmpty()) {
                continue;
            }
            double distance = from.distanceToSqr(living.position());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = living;
            }
        }
        return nearest;
    }

    /** Ends the flight against {@code target} and binds it as the anchor of this spear. */
    private void stickInto(Entity target, Vec3 from, Vec3 reach) {
        Vec3 stop = target.getBoundingBox().inflate(0.3D).clip(from, reach).orElse(reach);
        // Rest against the body instead of sinking into it, so the anchor stays readable.
        Vec3 direction = from.subtract(stop);
        if (direction.lengthSqr() > 1.0E-6D) {
            stop = stop.add(direction.normalize().scale(0.1D));
        }
        setPos(stop);
        setDeltaMovement(Vec3.ZERO);
        this.anchoredEntity = target.getUUID();
        // Remember where on the body the spear sits, so it can ride along without drifting.
        this.stickOffset = stop.subtract(target.position());
        // The client rides the creature itself, so it needs the id and offset, not a UUID it would
        // have to resolve against a level it may not have loaded yet.
        publishHost(target.getId(), this.stickOffset);
        anchor();
    }

    /**
     * Synced id of the creature the spear is stuck in, {@code -1} when it is anchored to the world.
     * Read by {@code DreamSpearFollowMixin} to ride the body on the client.
     */
    public int hostId() {
        return this.entityData.get(DATA_HOST_ID);
    }

    /** Synced offset the spear sits at on the host body. */
    public Vec3 hostOffset() {
        return new Vec3(this.entityData.get(DATA_OFFSET_X), this.entityData.get(DATA_OFFSET_Y),
                this.entityData.get(DATA_OFFSET_Z));
    }

    /**
     * Moves an entity-bound spear along with the creature it is stuck in.
     *
     * <p>An anchor in the world stays where it was placed, but an anchor in a creature is a rider:
     * the spear keeps the offset it landed with and follows the body. If the creature is gone, the
     * spear turns into a plain block anchor at its last position instead of hanging on a dead UUID.
     * </p>
     */
    private void followAnchoredEntity() {
        if (this.anchoredEntity == null) {
            return;
        }
        Entity anchor = level instanceof ServerLevel serverLevel
                ? serverLevel.getEntity(this.anchoredEntity)
                : null;
        if (anchor == null || !anchor.isAlive()) {
            this.anchoredEntity = null;
            this.stickOffset = null;
            this.entityData.set(DATA_HOST_ID, -1);
            // Keep anchoring, but now to the ground the spear last rested against.
            syncAnchorToOwner(this.owner, true);
            return;
        }
        Vec3 offset = this.stickOffset == null ? Vec3.ZERO : this.stickOffset;
        this.stickOffset = offset;
        // Keep the synced copy current: after a reload the client only learns the host from here.
        publishHost(anchor.getId(), offset);
        setPos(anchor.position().add(offset));
    }

    /** Publishes the ridden creature and the offset the spear sits at, for the client to follow. */
    private void publishHost(int hostId, Vec3 offset) {
        if (this.entityData.get(DATA_HOST_ID) != hostId) {
            this.entityData.set(DATA_HOST_ID, hostId);
        }
        if (this.entityData.get(DATA_OFFSET_X) != (float) offset.x) {
            this.entityData.set(DATA_OFFSET_X, (float) offset.x);
        }
        if (this.entityData.get(DATA_OFFSET_Y) != (float) offset.y) {
            this.entityData.set(DATA_OFFSET_Y, (float) offset.y);
        }
        if (this.entityData.get(DATA_OFFSET_Z) != (float) offset.z) {
            this.entityData.set(DATA_OFFSET_Z, (float) offset.z);
        }
    }

    /** Moves the forced chunk along with the spear when an anchored spear changes chunk. */
    private void ensureForcedChunk() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        ChunkPos current = new ChunkPos(blockPosition());
        if (this.chunkForced && current.equals(this.forcedChunk)) {
            return;
        }
        if (this.chunkForced) {
            releaseForcedChunk();
        }
        forceChunk(server, current);
        this.forcedChunk = current;
        this.chunkForced = true;
    }

    /**
     * Lays a trail of cherry petals along the path the spear travelled since the last client tick.
     *
     * <p>Client-only: the server never simulates the trail and no packet is spent on it. The spear
     * moves a couple of blocks per tick, so the segment between the previous and the current
     * position is filled with several petals instead of dropping one per tick - otherwise the trail
     * would read as separate dots. A pinned spear leaves no trail.</p>
     */
    private void spawnFlightTrail() {
        if (isAnchored()) {
            this.trailFrom = null;
            return;
        }
        Vec3 current = position();
        Vec3 from = this.trailFrom == null ? current : this.trailFrom;
        this.trailFrom = current;
        double distance = from.distanceTo(current);
        // Roughly one petal every half block, capped so a very fast spear cannot flood the world.
        int petals = Mth.clamp((int) Math.ceil(distance * 2.0D), 1, 8);
        for (int index = 0; index < petals; index++) {
            double t = (index + this.random.nextDouble()) / petals;
            double x = Mth.lerp(t, from.x, current.x) + (this.random.nextDouble() - 0.5D) * 0.25D;
            double y = Mth.lerp(t, from.y, current.y) + (this.random.nextDouble() - 0.5D) * 0.25D;
            double z = Mth.lerp(t, from.z, current.z) + (this.random.nextDouble() - 0.5D) * 0.25D;
            level.addParticle(DWObjects.DREAM_PETAL.get(), x, y, z, 0.0D, -0.02D, 0.0D);
        }
    }

    /**
     * Moves the spear through the dream terrain instead of the real one.
     *
     * <p>A spear thrown inside the dream has to stop on the dream floor. Vanilla movement reads the
     * real chunk, where the block the dreamer stands on may be missing entirely - the spear would
     * fly on forever or stop at a block nobody sees. The move is therefore swept against the dream
     * state of every covered position, and the spear comes to rest at the last free spot of the
     * step, so it neither passes through terrain nor jumps onto it.</p>
     *
     * @return whether the spear ran into something and has to anchor where it stands
     */
    private boolean moveOverDreamTerrain(Vec3 motion) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        Vec3 from = position();
        double distance = motion.length();
        int steps = Math.max(1, Mth.ceil(distance / SWEEP_STEP));
        for (int step = 1; step <= steps; step++) {
            Vec3 candidate = from.add(motion.scale((double) step / steps));
            if (dreamworld$collides(serverLevel, candidate)) {
                // Back off to the previous sample: the last position that was still free.
                setPos(from.add(motion.scale((double) (step - 1) / steps)));
                setDeltaMovement(Vec3.ZERO);
                return true;
            }
        }
        setPos(from.add(motion));
        this.hasImpulse = true;
        return false;
    }

    /** {@return whether the spear's box would overlap a solid dream block at {@code target}} */
    private boolean dreamworld$collides(ServerLevel level, Vec3 target) {
        AABB box = getBoundingBox().move(target.subtract(position()));
        DreamPatches patches = DreamPatches.get(level);
        for (BlockPos pos : BlockPos.betweenClosed(
                new BlockPos(box.minX, box.minY, box.minZ),
                new BlockPos(box.maxX, box.maxY, box.maxZ))) {
            BlockState state = patches.stateAt(level, pos);
            if (state.isAir()) {
                continue;
            }
            // Air is not the only thing without a collider: grass, flowers and torches are non-air
            // blocks with an empty shape, and asking an empty shape for its bounds throws.
            VoxelShape collision = state.getCollisionShape(level, pos);
            if (collision.isEmpty()) {
                continue;
            }
            if (collision.bounds().move(pos).intersects(box)) {
                return true;
            }
        }
        return false;
    }

    /** Pins the spear where it currently is, force-loads that chunk and binds it as the anchor. */
    public void anchor() {
        setAnchored(true);
        syncAnchorToOwner(this.owner, true);
    }

    /**
     * Binds or clears this spear as the thrower's dream anchor, so intrusions performed while
     * dreaming land on the spear instead of the dreamer's own coordinates. The layer the spear was
     * thrown on is handed over with it, because the anchor only redirects intrusions from the other
     * layer.
     */
    private void syncAnchorToOwner(@Nullable java.util.UUID ownerId, boolean bound) {
        if (!(level instanceof ServerLevel serverLevel) || ownerId == null) {
            return;
        }
        ServerPlayer ownerPlayer = serverLevel.getServer().getPlayerList().getPlayer(ownerId);
        if (ownerPlayer == null) {
            return;
        }
        if (!bound) {
            DreamManager.clearAnchor(ownerPlayer);
        } else if (this.anchoredEntity != null) {
            DreamManager.setEntityAnchor(ownerPlayer, this.anchoredEntity, dreamSide());
        } else {
            DreamManager.setBlockAnchor(ownerPlayer, serverLevel.dimension(), blockPosition(),
                    dreamSide());
        }
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    /**
     * Destroys this anchor: the binding is cleared, the chunk is released and the spear is removed
     * without returning the item.
     *
     * <p>This is the way out of the one-spear rule. A player who already has an anchor bound cannot
     * throw another spear, and the hotbar hint offers this as the alternative - the anchor is lost,
     * not picked up, so a throw is always an explicit trade.</p>
     */
    public void destroyAnchor() {
        syncAnchorToOwner(this.owner, false);
        releaseForcedChunk();
        discard();
    }

    /**
     * A spear is sent to both layers.
     *
     * <p>Unlike dream clay, which is filtered to the layer it belongs to, an anchor on the other
     * layer still has to reach the client: that is what lets it be drawn as a pale ghost at the
     * position it would occupy. Only the pickup is refused there, not the visibility.</p>
     */
    @Override
    public boolean broadcastToPlayer(ServerPlayer player) {
        return true;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        return pickUp(player) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    /**
     * Picks the spear back up for its owner.
     *
     * <p>Only from the layer it was thrown on. A spear anchored on the other layer is a ghost to
     * that player - it is visible as a pale outline but not present - so the pickup has to be
     * refused here as well, not just hidden on the client.</p>
     */
    public boolean pickUp(Player player) {
        if (level.isClientSide() || !isAnchored() || player.isSpectator()) {
            return false;
        }
        if (this.owner != null && !this.owner.equals(player.getUUID())) {
            return false;
        }
        if (!(player instanceof ServerPlayer serverPlayer)
                || dreamSide() != DreamManager.isDreaming(serverPlayer)) {
            return false;
        }
        player.getInventory().placeItemBackInInventory(new ItemStack(DWObjects.DREAM_ALLOY_SPEAR.get()));
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
        return new ItemStack(DWObjects.DREAM_ALLOY_SPEAR.get());
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(DWObjects.DREAM_ALLOY_SPEAR.get());
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

    /** {@return whether this spear was thrown on the dream layer} */
    public boolean dreamSide() {
        return this.entityData.get(DATA_DREAM_SIDE);
    }

    /** Marks the spear as belonging to the dream layer; set at throw time. */
    public void setDreamSide(boolean dreamSide) {
        this.entityData.set(DATA_DREAM_SIDE, dreamSide);
    }

    /** {@return whether the spear has pinned itself; synced so clients can stop the flight effects} */
    public boolean isAnchored() {
        return this.entityData.get(DATA_ANCHORED);
    }

    public void setAnchored(boolean anchored) {
        if (isAnchored() == anchored) {
            return;
        }
        this.entityData.set(DATA_ANCHORED, anchored);
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

    private void forceCurrentChunk() {
        if (level instanceof ServerLevel server) {
            ChunkPos pos = new ChunkPos(blockPosition());
            forceChunk(server, pos);
            this.forcedChunk = pos;
            this.chunkForced = true;
        }
    }

    private void releaseForcedChunk() {
        if (this.chunkForced && this.forcedChunk != null && level instanceof ServerLevel server) {
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
        if (tag.contains("StickX")) {
            this.stickOffset = new Vec3(tag.getDouble("StickX"), tag.getDouble("StickY"),
                    tag.getDouble("StickZ"));
        }
        setDreamSide(tag.getBoolean("DreamSide"));
        if (tag.getBoolean("Anchored")) {
            // Goes through the setter so the synced flag, physics and forced chunk stay in step.
            setAnchored(true);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.owner != null) {
            tag.putUUID("Owner", this.owner);
        }
        if (this.anchoredEntity != null) {
            tag.putUUID("AnchoredEntity", this.anchoredEntity);
        }
        if (this.stickOffset != null) {
            tag.putDouble("StickX", this.stickOffset.x);
            tag.putDouble("StickY", this.stickOffset.y);
            tag.putDouble("StickZ", this.stickOffset.z);
        }
        tag.putBoolean("DreamSide", dreamSide());
        tag.putBoolean("Anchored", isAnchored());
    }
}
