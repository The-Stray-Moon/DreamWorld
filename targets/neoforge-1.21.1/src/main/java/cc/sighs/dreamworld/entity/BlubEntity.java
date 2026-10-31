package cc.sighs.dreamworld.entity;

import cc.sighs.dreamworld.DWRegistry;
import cc.sighs.dreamworld.DreamWorldNeoForge121;
import cc.sighs.dreamworld.dream.DreamManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/**
 * Blub, the jelly rabbit: the only creature found in the dream world. It cannot be attacked, it
 * produces dream clay from time to time the way a chicken lays eggs, and it now and then lies down to
 * rest.
 *
 * <p>It must only be created through its spawn egg or by the dream side code, there is no natural
 * spawn placement for it. Blubs are shared by all dreaming players of a level and are never sent to a
 * client that is not dreaming.</p>
 */
@EventBusSubscriber(modid = DreamWorldNeoForge121.MOD_ID)
public class BlubEntity extends Rabbit {
    /** Shortest delay between two dream clay drops. */
    public static final int CLAY_DELAY_MIN = 20 * 60 * 4;
    /** Longest delay between two dream clay drops. */
    public static final int CLAY_DELAY_MAX = 20 * 60 * 6;
    /** Shortest stretch of standing still before the blub lies down. */
    private static final int REST_DELAY_MIN = 20 * 8;
    /** Longest stretch of standing still before the blub lies down. */
    private static final int REST_DELAY_MAX = 20 * 20;
    /** Shortest time spent lying down. */
    private static final int REST_TIME_MIN = 20 * 10;
    /** Longest time spent lying down. */
    private static final int REST_TIME_MAX = 20 * 25;

    private static final EntityDataAccessor<Boolean> DATA_RESTING =
            SynchedEntityData.defineId(BlubEntity.class, EntityDataSerializers.BOOLEAN);

    private int clayDelay = -1;
    /** Ticks spent standing still since the last walk. */
    private int stillTicks;
    /** Ticks the blub still wants to stay down. */
    private int restTicks;
    /** Random delay the next rest starts after. */
    private int nextRestDelay = -1;
    /** Previous tick's position, used to tell real movement from the AI asking for it. */
    private double lastX;
    private double lastZ;
    private boolean hasLastPos;
    /** Whether the entity already had AI disabled before the rest, so waking up can restore it. */
    private boolean noAiBeforeRest;

    public BlubEntity(EntityType<? extends BlubEntity> type, Level level) {
        super(type, level);
        // Dream blubs are the only dream creatures, they must not despawn on their own.
        setPersistenceRequired();
    }

    /**
     * The blub's own hopping movement, with one addition: a full block of step height.
     *
     * <p>The hop is what the model and its clips were drawn for, so the vanilla blub jump control
     * stays. What it cannot do on its own is climb a one-block step reliably - the hop is only a
     * fraction of a block while strolling - so the step height is raised instead, and the body simply
     * walks up a block the way a horse or a player with tall boots does.</p>
     */
    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(DWRegistry.BLUB.get(), Rabbit.createAttributes()
                .add(Attributes.STEP_HEIGHT, 1.0)
                .build());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_RESTING, false);
    }

    /** {@return whether the blub is currently lying down} */
    public boolean isResting() {
        return this.entityData.get(DATA_RESTING);
    }

    /**
     * {@return whether the blub is down and therefore must not move at all}
     *
     * <p>Two states put it on the ground: its own rest timer, and the sleeping pose an intrusion copy
     * is created with. The model draws both as lying, so both have to be pinned - a body that is
     * drawn flat but still walks, hops or gets shoved reads as a bug no matter which flag caused
     * it.</p>
     */
    public boolean isDown() {
        return isResting() || getPose() == Pose.SLEEPING;
    }

    private void setResting(boolean resting) {
        if (isResting() == resting) {
            return;
        }
        this.entityData.set(DATA_RESTING, resting);
        if (level().isClientSide) {
            return;
        }
        if (resting) {
            // Cut the AI for the duration: goals keep re-pathing while a mob is held in place, and
            // every one of those paths reruns the move control. A mob that does not run its AI cannot
            // wander off, cannot hop and cannot restart a path.
            this.noAiBeforeRest = isNoAi();
            setNoAi(true);
            holdStill();
        } else {
            setNoAi(this.noAiBeforeRest);
        }
    }

    /**
     * Cancels everything a downed blub could move with.
     *
     * <p>Movement input, the jump flag, a running path and any leftover velocity are cleared together,
     * because each of them alone is enough to move the body: the AI can ask for a hop without any
     * input, and an entity that was pushed carries that push into its next tick.</p>
     */
    private void holdStill() {
        this.jumping = false;
        this.xxa = 0.0F;
        this.zza = 0.0F;
        setJumping(false);
        setDeltaMovement(Vec3.ZERO);
        getNavigation().stop();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (isDown()) {
            // A resting blub still lays clay; only movement is off.
            holdStill();
            tickClay();
            if (isResting() && --this.restTicks <= 0) {
                setResting(false);
                this.stillTicks = 0;
                this.nextRestDelay = nextRestDelay();
            }
            return;
        }
        boolean moved = hasLastPos && horizontalMoved();
        this.lastX = getX();
        this.lastZ = getZ();
        this.hasLastPos = true;
        tickClay();
        tickRest(moved);
    }

    /** {@return whether the blub actually changed position this tick, ignoring vertical motion} */
    private boolean horizontalMoved() {
        double dx = getX() - this.lastX;
        double dz = getZ() - this.lastZ;
        return dx * dx + dz * dz > 1.0E-4;
    }

    /**
     * A downed blub does not walk anywhere.
     *
     * <p>Zeroing the delta after {@code super.tick()} would be too late, the body has already been
     * moved by then; cutting the movement input here is what actually pins it to the ground while it
     * lies down.</p>
     */
    @Override
    public void travel(Vec3 input) {
        super.travel(isDown() ? Vec3.ZERO : input);
    }

    /**
     * A downed blub does not hop either.
     *
     * <p>Rabbit AI starts a jump whenever its move control wants to go somewhere, and that impulse is
     * applied in {@code aiStep} before {@code travel}, so cutting the travel input alone would leave
     * it bouncing on the spot.</p>
     */
    @Override
    public void jumpFromGround() {
        if (isDown()) {
            setJumping(false);
            return;
        }
        super.jumpFromGround();
    }

    /** A downed blub must not be shoved around by players or other mobs. */
    @Override
    public boolean isPushable() {
        return !isDown() && super.isPushable();
    }

    /** The other half of push immunity: a push is a velocity write, so it is refused here too. */
    @Override
    public void push(double x, double y, double z) {
        if (isDown()) {
            return;
        }
        super.push(x, y, z);
    }

    private void tickClay() {
        if (this.clayDelay < 0) {
            this.clayDelay = nextClayDelay();
        }
        if (--this.clayDelay <= 0) {
            if (level() instanceof ServerLevel server) {
                DreamClayEntity.spawnNear(server, position().add(0.0D, 0.4D, 0.0D), true);
            }
            this.clayDelay = nextClayDelay();
        }
    }

    /**
     * A downed blub runs no AI step at all.
     *
     * <p>{@code aiStep} is where the goals, the move control, the jump control, gravity and
     * {@code travel} all live - and the jump impulse is applied there, before {@code travel} ever
     * sees the input. Cutting the input alone left the blub hopping on the spot, so the whole step is
     * skipped while it lies down. The pose is synced separately, so the client keeps drawing it lying
     * down.</p>
     */
    @Override
    public void aiStep() {
        if (isDown()) {
            holdStill();
            return;
        }
        super.aiStep();
    }

    /**
     * Idle blubs lie down after a while and get back up when the timer runs out.
     *
     * <p>The state is synced so the client can play the matching animation, and the blub itself is
     * held in place while down: navigation is dropped, AI and its physics step are skipped, and the
     * motion is zeroed, which is what the {@code LIE_DOWN} / {@code LYING} / {@code GET_UP} clips are
     * drawn for. The timer itself is run by {@link #tick()}, together with the hold.</p>
     *
     * <p>Idleness is measured on the position, not on the navigation state: the AI keeps trying to
     * path while the blub is held down, so a navigation check would end the rest immediately. A blub
     * in the air never starts resting, because a skipped AI step also skips gravity.</p>
     */
    private void tickRest(boolean moved) {
        if (moved || !onGround()) {
            this.stillTicks = 0;
            return;
        }
        if (this.nextRestDelay < 0) {
            this.nextRestDelay = nextRestDelay();
        }
        if (++this.stillTicks < this.nextRestDelay) {
            return;
        }
        setResting(true);
        this.restTicks = REST_TIME_MIN + random.nextInt(REST_TIME_MAX - REST_TIME_MIN + 1);
        this.stillTicks = 0;
    }

    private int nextRestDelay() {
        return REST_DELAY_MIN + random.nextInt(REST_DELAY_MAX - REST_DELAY_MIN + 1);
    }

    private int nextClayDelay() {
        return CLAY_DELAY_MIN + random.nextInt(CLAY_DELAY_MAX - CLAY_DELAY_MIN + 1);
    }

    @Override
    public Rabbit getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return DWRegistry.BLUB.get().create(level);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    /**
     * Server-side tracking gate. The chunk map asks every entity whether it may be sent to a
     * player, so a blub only ever reaches the clients of dreaming players. Returning false also
     * takes the blub out of the tracking set of a player who just woke up, which is what sends
     * them the removal packet.
     */
    @Override
    public boolean broadcastToPlayer(ServerPlayer player) {
        return DreamManager.isDreaming(player);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }
}
