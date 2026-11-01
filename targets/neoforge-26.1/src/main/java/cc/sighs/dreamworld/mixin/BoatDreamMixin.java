package cc.sighs.dreamworld.mixin;

import cc.sighs.dreamworld.boat.DreamBoatGate;
import cc.sighs.dreamworld.boat.DreamTunnelBoat;
import cc.sighs.dreamworld.boat.TunnelGeometry;
import cc.sighs.dreamworld.client.DreamClientState;
import cc.sighs.dreamworld.spi.DreamPlatform;
import cc.sighs.dreamworld.dream.DreamEntities;
import cc.sighs.dreamworld.dream.DreamManager;
import cc.sighs.dreamworld.dream.GraffitiRegistry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Dream boat: a boat ridden by a dreaming player glides over every block, can jump, climbs walls and
 * locks onto the graffiti tunnels.
 *
 * <p>The glide is deliberately below blue ice: full blue-ice slipperiness leaves the boat with so
 * much momentum that turning cannot keep up with it, which reads as "slides out of control" rather
 * than "rides smoothly". The dream friction, the coast drag and the turn clamp together make the
 * boat fast - well above walking speed - but steerable.</p>
 *
 * <p>All effects are gated on the controlling passenger being a dreaming player, so ordinary
 * boating is untouched. The boat is simulated by whichever side controls it (the owning client
 * for the rider, the server for the same boat), so the gate has to work on both sides.</p>
 *
 * <p>Inside a graffiti tunnel the boat is still the player's to drive: its own thrust, friction and
 * speed are untouched, and the tunnel only removes whatever part of the motion does not run along
 * its axis. The line fixes the direction; the pacing stays in the player's hands.</p>
 */
@Mixin(AbstractBoat.class)
public abstract class BoatDreamMixin implements DreamTunnelBoat {
    /**
     * Ground friction on the dream layer.
     *
     * <p>Vanilla multiplies the horizontal velocity by this value every tick and pushes the boat with
     * a fixed thrust, so the cruising speed is {@code thrust / (1 - friction)}: 0.6 is a slow land
     * boat, 0.9 is ice, 0.989 is blue ice and about 70 blocks per second. This lands at roughly 18
     * blocks per second - about three times a sprint - so the boat is fast while turning can still
     * keep up with it.</p>
     */
    private static final float DREAM_FRICTION = 0.9465F;
    /**
     * Tunnel friction: blue ice.
     *
     * <p>A graffiti tunnel is meant to be the fastest surface in the dream, so the boat keeps what
     * little momentum blue ice gives instead of the ordinary dream drag. The vanilla thrust is a
     * fixed 0.04 per tick, which with this friction reaches the same speed a boat reaches on blue
     * ice in the overworld.</p>
     */
    private static final float TUNNEL_FRICTION = 0.989F;
    /** Top speed the vanilla thrust reaches on the tunnel friction: {@code thrust / (1 - friction)}. */
    private static final double TUNNEL_TOP_SPEED = 0.04D / (1.0D - TUNNEL_FRICTION);
    /**
     * Fraction of the remaining gap to {@link #TUNNEL_TOP_SPEED} the start thrust closes per tick.
     *
     * <p>Blue ice keeps its speed almost for free, which also means the vanilla thrust of 0.04 per
     * tick needs dozens of ticks to get going - far too slow for a tunnel that is usually short.
     * Rowing forward therefore adds a proportional push on top of it: the boat leaves the mouth it
     * entered with some speed instead of crawling.</p>
     */
    private static final double TUNNEL_RAMP = 0.12D;
    /** Extra horizontal drag per tick while the boat rests on the ground with no paddle held. */
    private static final double DREAM_COAST_DRAG = 0.90D;
    /** Extra forward push per tick on top of the vanilla paddling force. */
    private static final double DREAM_EXTRA_THRUST = 0.008D;
    /**
     * Largest turning impulse per tick. The turn impulse accumulates: its steady state is
     * {@code friction / (1 - friction)} degrees per tick, which is 90 on blue ice - the boat would
     * spin out. The clamp turns that into a fast but predictable circle.
     */
    private static final float DREAM_MAX_TURN = 7.0F;
    /** Upward speed added per tick while the boat grinds against a tall wall. */
    private static final double DREAM_CLIMB_SPEED = 0.12D;
    /** Upper bound of the wall-climbing speed. */
    private static final double DREAM_CLIMB_MAX = 0.5D;
    /**
     * Highest step the boat rides up with the assisted lift, in blocks.
     *
     * <p>A full block plus the clearance margin, matching the smooth-step reference: every ordinary
     * terrain step is covered, anything taller falls back to the wall climb.</p>
     */
    private static final double DREAM_STEP_HEIGHT = 1.05D;
    /** Extra room required above the target surface. */
    private static final double DREAM_STEP_CLEARANCE = 0.04D;
    /** How far below the hull a surface may be to still count as support under the boat. */
    private static final double DREAM_MAX_GROUND_DISTANCE = 1.5D;
    /** Look-ahead used to find the step target, in ticks of current travel. */
    private static final double DREAM_STEP_PREDICTION_TICKS = 10.0D;
    private static final double DREAM_STEP_LOOKAHEAD_MIN = 0.8D;
    /**
     * Longest step look-ahead, in blocks.
     *
     * <p>The boat covers up to about half a block per tick once it is up to speed, so a one block
     * step needs several ticks of warning to be climbed without touching it. A short probe only ever
     * sees the step once the hull is already against it.</p>
     */
    private static final double DREAM_STEP_LOOKAHEAD_MAX = 2.5D;
    /** Ceiling of the assisted lift velocity. */
    private static final double DREAM_STEP_MAX_LIFT = 0.55D;
    /** Vertical speed every assisted lift starts from, so the hull always leaves the ground. */
    private static final double DREAM_STEP_BASE_LIFT = 0.05D;
    /** Ticks of the remaining approach that are treated as already spent. */
    private static final double DREAM_STEP_ARRIVE_MARGIN = 0.5D;
    /** Ticks the assist keeps pushing after the obstacle has left the probe. */
    private static final int DREAM_STEP_HOLD_TICKS = 8;
    /** Smallest horizontal speed at which the boat is considered to be driving into terrain. */
    private static final double DREAM_STEP_MIN_SPEED = 0.05D;
    /**
     * Speed the look-ahead pretends the boat has when it is already pressed into a block. The
     * collision zeroes the real velocity, and without this the terrain search would never reach the
     * block that is holding the boat back.
     */
    private static final double DREAM_STEP_BLOCKED_SPEED = 0.28D;
    /** How far ahead the boat probes for a wall when it is too tall to ride up in one step. */
    private static final double DREAM_WALL_PROBE = 0.15D;
    /** Vertical impulse of a dream boat jump. */
    private static final double DREAM_JUMP_POWER = 0.5D;
    /** Cooldown between two jumps, in ticks. */
    private static final int DREAM_JUMP_COOLDOWN = 8;
    /** Distance the boat is pushed away from a tunnel when it leaves one. */
    private static final double TUNNEL_ESCAPE_DISTANCE = 6.0D;
    /** How deep the boat looks for a place to land, in blocks. */
    private static final int LANDING_SCAN_DEPTH = 48;

    @Shadow
    private float deltaRotation;
    @Shadow
    private boolean inputDown;
    @Shadow
    private AbstractBoat.Status status;
    @Shadow
    private AbstractBoat.Status oldStatus;

    @Unique
    private int dreamworld$assistTicks;
    @Unique
    private double dreamworld$lastTargetY;
    @Unique
    private double dreamworld$lastDistance;
    @Unique
    private boolean dreamworld$inTunnel;
    @Unique
    private Vec3 dreamworld$tunnelStart = Vec3.ZERO;
    @Unique
    private Vec3 dreamworld$tunnelDir = Vec3.ZERO;
    @Unique
    private double dreamworld$tunnelProgress;
    @Unique
    private double dreamworld$tunnelLength;
    @Unique
    @Nullable
    private TunnelGeometry.Pair dreamworld$tunnelPair;
    @Unique
    private int dreamworld$jumpCooldown;
    @Unique
    private boolean dreamworld$wasJumping;
    // ------------------------------------------------------------- dream gate

    /** A boat is a dream boat only while a dreaming player controls it. */
    @Unique
    private static boolean dreamworld$isDreamRider(@Nullable Entity rider) {
        return DreamBoatGate.isDreamRider(rider);
    }

    // ------------------------------------------------------------- blue ice speed

    @Inject(require = 0, method = "getGroundFriction", at = @At("RETURN"), cancellable = true)
    private void dreamworld$iceFriction(CallbackInfoReturnable<Float> cir) {
        AbstractBoat boat = (AbstractBoat) (Object) this;
        if (!dreamworld$isDreamRider(boat.getControllingPassenger())) {
            return;
        }
        float friction = cir.getReturnValueF();
        if (dreamworld$inTunnel) {
            // A tunnel is blue ice: the boat keeps its speed along the line almost for free.
            cir.setReturnValue(Float.isNaN(friction) ? TUNNEL_FRICTION : Math.max(friction, TUNNEL_FRICTION));
            return;
        }
        if (Float.isNaN(friction)) {
            // Nothing below the boat: the dream boat glides anyway, so it keeps the same cruise
            // speed instead of falling back to vanilla air drag.
            cir.setReturnValue(DREAM_FRICTION);
            return;
        }
        cir.setReturnValue(Math.max(friction, DREAM_FRICTION));
    }

    /**
     * Lets a falling dream boat land on water instead of diving through it.
     *
     * <p>Vanilla stops a boat at the surface with its own entry snap, and that snap only runs when
     * the previous status was {@code IN_AIR}. The dream friction above overrides
     * {@code getGroundFriction} to report ground everywhere, so a dream boat is never {@code IN_AIR} -
     * the snap is skipped, the boat keeps its fall speed and drives straight to the bottom, where the
     * water at its hull bottom is the pond floor and buoyancy never gets a chance. Repeating the
     * vertical part of the snap here is enough to make it float.</p>
     */
    @Inject(require = 0, method = "floatBoat", at = @At("HEAD"))
    private void dreamworld$floatOnEntry(CallbackInfo ci) {
        AbstractBoat boat = (AbstractBoat) (Object) this;
        if (this.status != AbstractBoat.Status.IN_WATER || this.oldStatus == AbstractBoat.Status.IN_WATER) {
            return;
        }
        if (!dreamworld$isDreamRider(boat.getControllingPassenger())) {
            return;
        }
        Vec3 velocity = boat.getDeltaMovement();
        if (velocity.y < 0.0D) {
            boat.setDeltaMovement(velocity.x, 0.0D, velocity.z);
        }
    }

    @Inject(require = 0, method = "floatBoat", at = @At("TAIL"))
    private void dreamworld$extraThrust(CallbackInfo ci) {
        AbstractBoat boat = (AbstractBoat) (Object) this;
        if (dreamworld$inTunnel || boat.isInWater()) {
            return;
        }
        if (!dreamworld$isDreamRider(boat.getControllingPassenger())) {
            return;
        }
        boolean paddling = boat.getPaddleState(AbstractBoat.PADDLE_LEFT) || boat.getPaddleState(AbstractBoat.PADDLE_RIGHT);
        if (!paddling) {
            // Nothing is being rowed: bleed the remaining speed against the ground so the boat stops
            // where the driver wants it instead of skating on for another dozen blocks.
            if (boat.onGround()) {
                Vec3 coasting = boat.getDeltaMovement();
                boat.setDeltaMovement(coasting.x * DREAM_COAST_DRAG, coasting.y,
                        coasting.z * DREAM_COAST_DRAG);
            }
            return;
        }
        float yaw = boat.getYRot() * Mth.DEG_TO_RAD;
        boat.setDeltaMovement(boat.getDeltaMovement().add(
                -Mth.sin(yaw) * DREAM_EXTRA_THRUST, 0.0D, Mth.cos(yaw) * DREAM_EXTRA_THRUST));
    }

    /**
     * Runs the climb between vanilla boat physics and the boat's move, so a lift applied here is
     * used by this tick's collision resolution instead of arriving one tick late.
     */
    @Inject(require = 0, method = "floatBoat", at = @At("TAIL"))
    private void dreamworld$climbInFloat(CallbackInfo ci) {
        AbstractBoat boat = (AbstractBoat) (Object) this;
        if (dreamworld$inTunnel || boat.isInWater()) {
            return;
        }
        if (!dreamworld$isDreamRider(boat.getControllingPassenger())) {
            return;
        }
        dreamworld$climbTerrain(boat);
    }

    /**
     * Lets a tunnel rider look around freely.
     *
     * <p>{@code clampRotation} pulls the rider's yaw back to within 105 degrees of the boat's bow.
     * That is fine while the driver can turn the boat to match, but inside a tunnel the bow is fixed,
     * so looking over the shoulder would be snapped back every tick - one of the reasons the view
     * seemed to turn on its own.</p>
     */
    @Inject(require = 0, method = "clampRotation", at = @At("HEAD"), cancellable = true)
    private void dreamworld$freeLookInTunnel(Entity rider, CallbackInfo ci) {
        if (dreamworld$inTunnel) {
            ci.cancel();
        }
    }

    /**
     * Limits the turn rate, and cancels it entirely inside a tunnel.
     *
     * <p>Inside a tunnel the bow is pinned to the axis, so a turn has nowhere to go - but vanilla
     * still accumulates {@code deltaRotation} and {@code AbstractBoat#positionRider} adds that value to the
     * rider's own yaw every tick. The boat would keep its heading while the player's view kept
     * spinning. Zeroing the turn is what stops the camera from rotating on its own.</p>
     */
    @Inject(require = 0, method = "controlBoat", at = @At("TAIL"))
    private void dreamworld$clampTurn(CallbackInfo ci) {
        AbstractBoat boat = (AbstractBoat) (Object) this;
        if (!dreamworld$isDreamRider(boat.getControllingPassenger())) {
            return;
        }
        if (dreamworld$inTunnel) {
            this.deltaRotation = 0.0F;
            if (this.inputDown) {
                // Backing up has no meaning inside a tunnel: the oars just brake the boat.
                boat.setDeltaMovement(Vec3.ZERO);
                return;
            }
            dreamworld$tunnelStartThrust(boat);
            return;
        }
        this.deltaRotation = Mth.clamp(this.deltaRotation, -DREAM_MAX_TURN, DREAM_MAX_TURN);
    }

    /**
     * Extra thrust while rowing forward in a tunnel, so the boat gets going quickly.
     *
     * <p>The push is proportional to how far the boat still is from {@link #TUNNEL_TOP_SPEED}, so it
     * accelerates hard from a standstill and fades out as the boat reaches cruise speed instead of
     * overshooting it. Rowing is the forward input, which vanilla reports as both paddles at once;
     * turning gives a single paddle and adds nothing.</p>
     */
    @Unique
    private void dreamworld$tunnelStartThrust(AbstractBoat boat) {
        if (!boat.getPaddleState(AbstractBoat.PADDLE_LEFT) || !boat.getPaddleState(AbstractBoat.PADDLE_RIGHT)) {
            return;
        }
        Vec3 delta = boat.getDeltaMovement();
        double shortfall = TUNNEL_TOP_SPEED - delta.dot(dreamworld$tunnelDir);
        if (shortfall <= 0.0D) {
            return;
        }
        boat.setDeltaMovement(delta.add(dreamworld$tunnelDir.scale(shortfall * TUNNEL_RAMP)));
    }

    // ------------------------------------------------------------- jump / climb / tunnel

    @Inject(require = 0, method = "tick", at = @At("TAIL"))
    private void dreamworld$dreamTick(CallbackInfo ci) {
        AbstractBoat boat = (AbstractBoat) (Object) this;
        if (dreamworld$jumpCooldown > 0) {
            dreamworld$jumpCooldown--;
        }
        LivingEntity rider = boat.getControllingPassenger();
        if (!dreamworld$isDreamRider(rider)) {
            if (dreamworld$inTunnel) {
                dreamworld$leaveTunnel(boat, false);
            }
            dreamworld$wasJumping = false;
            return;
        }
        if (!boat.isLocalInstanceAuthoritative()) {
            return;
        }
        boolean jumping = rider != null && ((DWLivingEntityAccessor) rider).dreamworld$isJumping();
        boolean jumpPressed = jumping && !dreamworld$wasJumping;
        dreamworld$wasJumping = jumping;

        if (dreamworld$inTunnel) {
            if (jumpPressed) {
                dreamworld$leaveTunnel(boat, true);
                dreamworld$jumpCooldown = DREAM_JUMP_COOLDOWN;
            } else {
                dreamworld$holdTunnel(boat);
                return;
            }
        }        if (jumpPressed && dreamworld$jumpCooldown == 0 && (boat.onGround() || boat.isInWater())) {
            Vec3 delta = boat.getDeltaMovement();
            boat.setDeltaMovement(delta.x, DREAM_JUMP_POWER, delta.z);
            dreamworld$jumpCooldown = DREAM_JUMP_COOLDOWN;
        }
    }

    /**
     * Rides the boat up steps and slopes.
     *
     * <p>Ported from the smooth-step reference: predict the surface the hull is about to run into,
     * make sure it really blocks the sweep path, and then push upwards with a velocity proportional
     * to the height gap. A plain constant impulse - what this used to do - is what made a one-block
     * step feel like grinding: the hull crawled up over a dozen ticks and slid back when the oars
     * stopped.</p>
     *
     * <p>The assist is held for a few ticks after the obstacle leaves the probe, because the hull
     * needs more than the tick it was detected in to actually reach the top.</p>
     *
     * <p>Walls taller than a step are not assistable and fall through to the climb impulse, which
     * keeps lifting while the boat pushes into something with room above it.</p>
     */
    @Unique
    private void dreamworld$climbTerrain(AbstractBoat boat) {
        // Only while rowing forward: turning in place must not start a climb.
        if (!boat.getPaddleState(AbstractBoat.PADDLE_LEFT) || !boat.getPaddleState(AbstractBoat.PADDLE_RIGHT)) {
            dreamworld$assistTicks = 0;
            return;
        }
        Vec3 delta = boat.getDeltaMovement();
        double speed = delta.horizontalDistance();
        Vec3 direction;
        if (speed >= DREAM_STEP_MIN_SPEED) {
            direction = new Vec3(delta.x, 0.0D, delta.z).normalize();
        } else {
            // Pressed into the block: the velocity is already gone, so aim by the bow.
            float yawRadians = boat.getYRot() * ((float) Math.PI / 180.0F);
            direction = new Vec3(-Mth.sin(yawRadians), 0.0D, Mth.cos(yawRadians));
        }
        AABB box = boat.getBoundingBox();

        // Only ride up terrain the boat is resting on; dropping next to a wall must not catch it.
        // A running assist keeps the gate open: the lift itself makes the hull airborne, and a
        // descent that is already on its way to a step must not cancel the climb halfway.
        if (boat.onGround() || delta.y > -0.15D || dreamworld$assistTicks > 0) {
            if (dreamworld$stepAssist(boat, box, direction, speed)) {
                return;
            }
        }

        // Taller than a step: keep climbing while the boat pushes into something with room above.
        AABB ahead = box.move(direction.x * DREAM_WALL_PROBE, 0.0D, direction.z * DREAM_WALL_PROBE);
        AABB above = box.move(0.0D, DREAM_CLIMB_SPEED, 0.0D);
        if (!boat.level().noCollision(boat, ahead) && boat.level().noCollision(boat, above)) {
            dreamworld$lift(boat, delta);
        }
    }

    /**
     * Applies the smooth step lift, or keeps the previous one running.
     *
     * @return whether a lift was applied
     */
    @Unique
    private boolean dreamworld$stepAssist(AbstractBoat boat, AABB box, Vec3 direction, double speed) {
        double predictionSpeed = Math.max(speed, DREAM_STEP_BLOCKED_SPEED);
        double lookAhead = Mth.clamp(predictionSpeed * DREAM_STEP_PREDICTION_TICKS,
                DREAM_STEP_LOOKAHEAD_MIN, DREAM_STEP_LOOKAHEAD_MAX);

        OptionalDouble support = dreamworld$predictedSupportTop(boat, box, direction, lookAhead);
        if (support.isPresent()) {
            double targetTop = support.getAsDouble();
            double liftHeight = targetTop - box.minY;
            if (liftHeight > 0.05D && liftHeight <= DREAM_STEP_HEIGHT) {
                AABB scan = box.expandTowards(direction.x * lookAhead, DREAM_STEP_HEIGHT,
                        direction.z * lookAhead).inflate(0.08D, 0.0D, 0.08D);
                Vec3 sweep = direction.scale(lookAhead);
                double bestDistance = Double.MAX_VALUE;
                boolean blocked = false;
                for (VoxelShape shape : boat.level().getBlockCollisions(boat, scan)) {
                    for (AABB part : shape.toAabbs()) {
                        OptionalDouble distance = dreamworld$sweepDistance(box, part, sweep, targetTop);
                        if (distance.isEmpty()) {
                            continue;
                        }
                        if (!dreamworld$hasHeadroom(boat, box, targetTop)) {
                            continue;
                        }
                        if (distance.getAsDouble() < bestDistance) {
                            bestDistance = distance.getAsDouble();
                            blocked = true;
                        }
                    }
                }
                if (blocked) {
                    dreamworld$assistTicks = DREAM_STEP_HOLD_TICKS;
                    dreamworld$lastTargetY = targetTop;
                    dreamworld$lastDistance = bestDistance;
                    dreamworld$applyStepLift(boat, speed, targetTop, bestDistance, lookAhead);
                    return true;
                }
            }
        }

        if (dreamworld$assistTicks > 0 && dreamworld$lastTargetY > box.minY) {
            dreamworld$assistTicks--;
            dreamworld$applyStepLift(boat, speed, dreamworld$lastTargetY, dreamworld$lastDistance,
                    lookAhead);
            return true;
        }
        dreamworld$assistTicks = 0;
        return false;
    }

    /**
     * Highest surface under the swept footprint that is at most one step above the hull.
     *
     * <p>Bounded below as well, so a boat gliding over a drop does not treat the distant ground as
     * its support.</p>
     */
    @Unique
    private static OptionalDouble dreamworld$predictedSupportTop(AbstractBoat boat, AABB box, Vec3 direction,
            double lookAhead) {
        AABB footprint = box.expandTowards(direction.x * lookAhead, 0.0D, direction.z * lookAhead)
                .inflate(0.05D, 0.0D, 0.05D);
        AABB search = new AABB(footprint.minX, box.minY - DREAM_MAX_GROUND_DISTANCE, footprint.minZ,
                footprint.maxX, box.minY + DREAM_STEP_HEIGHT, footprint.maxZ);
        double highest = Double.NEGATIVE_INFINITY;
        for (VoxelShape shape : boat.level().getBlockCollisions(boat, search)) {
            for (AABB part : shape.toAabbs()) {
                if (part.maxY <= box.minY + DREAM_STEP_HEIGHT && part.maxY > highest) {
                    highest = part.maxY;
                }
            }
        }
        return highest != Double.NEGATIVE_INFINITY && box.minY - highest < DREAM_MAX_GROUND_DISTANCE
                ? OptionalDouble.of(highest)
                : OptionalDouble.empty();
    }

    /**
     * Fraction of the sweep at which {@code obstacle} would be entered, if it is in the way.
     *
     * <p>Only obstacles that rise above the hull and stay below the target surface count: those are
     * the ones that would stop the boat before it reached the step it is aiming at.</p>
     */
    @Unique
    private static OptionalDouble dreamworld$sweepDistance(AABB movingBox, AABB obstacle, Vec3 sweep,
            double targetTop) {
        if (obstacle.maxY <= movingBox.minY + 0.05D || obstacle.minY >= targetTop) {
            return OptionalDouble.empty();
        }
        double entryX;
        double exitX;
        double entryZ;
        double exitZ;
        if (Math.abs(sweep.x) < 1.0E-7D) {
            if (movingBox.maxX <= obstacle.minX || movingBox.minX >= obstacle.maxX) {
                return OptionalDouble.empty();
            }
            entryX = Double.NEGATIVE_INFINITY;
            exitX = Double.POSITIVE_INFINITY;
        } else if (sweep.x > 0.0D) {
            entryX = (obstacle.minX - movingBox.maxX) / sweep.x;
            exitX = (obstacle.maxX - movingBox.minX) / sweep.x;
        } else {
            entryX = (obstacle.maxX - movingBox.minX) / sweep.x;
            exitX = (obstacle.minX - movingBox.maxX) / sweep.x;
        }
        if (Math.abs(sweep.z) < 1.0E-7D) {
            if (movingBox.maxZ <= obstacle.minZ || movingBox.minZ >= obstacle.maxZ) {
                return OptionalDouble.empty();
            }
            entryZ = Double.NEGATIVE_INFINITY;
            exitZ = Double.POSITIVE_INFINITY;
        } else if (sweep.z > 0.0D) {
            entryZ = (obstacle.minZ - movingBox.maxZ) / sweep.z;
            exitZ = (obstacle.maxZ - movingBox.minZ) / sweep.z;
        } else {
            entryZ = (obstacle.maxZ - movingBox.minZ) / sweep.z;
            exitZ = (obstacle.minZ - movingBox.maxZ) / sweep.z;
        }
        double entry = Math.max(Math.min(entryX, exitX), Math.min(entryZ, exitZ));
        double exit = Math.min(Math.max(entryX, exitX), Math.max(entryZ, exitZ));
        if (entry > exit || exit < 0.0D || entry > 1.0D) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(Mth.clamp(entry, 0.0D, 1.0D));
    }

    /** {@return whether the hull fits once lifted onto {@code targetTop}} */
    @Unique
    private static boolean dreamworld$hasHeadroom(AbstractBoat boat, AABB box, double targetTop) {
        double lift = targetTop - box.minY + DREAM_STEP_CLEARANCE;
        if (lift <= 0.0D || lift > DREAM_STEP_HEIGHT + DREAM_STEP_CLEARANCE) {
            return false;
        }
        return boat.level().noCollision(boat, box.move(0.0D, lift, 0.0D).deflate(0.02D));
    }

    /**
     * Pushes the hull upwards fast enough to clear the step before it arrives.
     *
     * <p>The lift is not a fixed impulse: the hull knows how much height it has to gain and how many
     * ticks are left before the sweep reaches the obstacle, so the vertical speed is the gap divided
     * by the remaining time. Far away that is a gentle nudge, close in it approaches the ceiling -
     * which is exactly the behaviour needed to never arrive at the step's face while still climbing.
     * </p>
     */
    @Unique
    private static void dreamworld$applyStepLift(AbstractBoat boat, double speed, double targetTop,
            double distance, double lookAhead) {
        Vec3 movement = boat.getDeltaMovement();
        AABB box = boat.getBoundingBox();
        double heightGap = Mth.clamp(targetTop - box.minY, 0.0D, DREAM_STEP_HEIGHT);
        // Blocks left before the hull reaches the obstacle, and the ticks that buys.
        double blocksToHit = Mth.clamp(distance, 0.0D, 1.0D) * lookAhead;
        double ticksToHit = blocksToHit / Math.max(speed, DREAM_STEP_BLOCKED_SPEED);
        double climbTicks = Math.max(ticksToHit - DREAM_STEP_ARRIVE_MARGIN, 0.5D);
        double lift = Mth.clamp(heightGap / climbTicks + DREAM_STEP_BASE_LIFT,
                DREAM_STEP_BASE_LIFT, DREAM_STEP_MAX_LIFT);
        if (movement.y < lift) {
            boat.setDeltaMovement(movement.x, lift, movement.z);
        }
    }

    /** Adds the climb impulse to the boat, keeping the horizontal part of its motion. */
    @Unique
    private static void dreamworld$lift(AbstractBoat boat, Vec3 delta) {
        double lift = Math.min(delta.y + DREAM_CLIMB_SPEED, DREAM_CLIMB_MAX);
        if (delta.y < lift) {
            boat.setDeltaMovement(delta.x, lift, delta.z);
        }
    }

    // ------------------------------------------------------------- graffiti tunnels

    /**
     * Attaches the boat to the graffiti tunnel closest to it, on request.
     *
     * <p>Nothing happens automatically any more: the rider asks for the tunnel with a right click, and
     * the boat only takes it when it is close enough and there is a usable stretch ahead. That keeps
     * a boat from being yanked off the ground every time it happens to pass under a tunnel.</p>
     */
    @Override
    public boolean attachDreamTunnel(@Nullable TunnelGeometry.Pair preferred) {
        AbstractBoat boat = (AbstractBoat) (Object) this;
        if (!dreamworld$isDreamRider(boat.getControllingPassenger())) {
            return false;
        }
        return dreamworld$attachTunnel(boat, boat.getControllingPassenger(), preferred);
    }

    /**
     * Attaches the boat to a tunnel within reach.
     *
     * <p>No line of sight is required: a tunnel usually runs through terrain, so the way between the
     * boat and the line is almost never clear - only the distance to the line counts, and that is
     * the rider's own interaction reach plus a block.</p>
     *
     * @param preferred the tunnel the rider pointed at, or {@code null} for the nearest one; the
     *                  line already being ridden is never picked again, so a rider can switch
     */
    @Unique
    private boolean dreamworld$attachTunnel(AbstractBoat boat, @Nullable LivingEntity rider,
            @Nullable TunnelGeometry.Pair preferred) {
        List<BlockPos> anchors = dreamworld$tunnelAnchors(boat);
        if (anchors.size() < 2) {
            return false;
        }
        Vec3 position = boat.position();
        int limit = dreamworld$tunnelLimit(boat);
        TunnelGeometry.Pair riding = dreamworld$inTunnel ? dreamworld$tunnelPair : null;
        TunnelGeometry.Tunnel tunnel = preferred == null
                ? TunnelGeometry.nearest(anchors, position, limit, riding)
                : TunnelGeometry.forPair(anchors, preferred, position, limit);
        if (tunnel == null) {
            return false;
        }
        if (riding != null && dreamworld$rides(tunnel)) {
            // Already on this line: re-attaching would only snap the boat along it.
            return false;
        }
        double reach = dreamworld$attachReach(boat);
        if (tunnel.distanceSq() > reach * reach) {
            return false;
        }
        double length = tunnel.length();
        if (length < 2.0D) {
            return false;
        }
        Vec3 direction = tunnel.direction();
        double remaining;
        // The boat travels towards the graffiti its bow points at.
        if (direction.dot(boat.getViewVector(1.0F)) < 0.0D) {
            direction = direction.reverse();
            remaining = tunnel.remaining(-1.0D);
        } else {
            remaining = tunnel.remaining(1.0D);
        }
        if (remaining < 2.0D) {
            return false;
        }
        Vec3 projection = tunnel.projection();
        dreamworld$inTunnel = true;
        dreamworld$tunnelPair = tunnel.pair();
        dreamworld$tunnelStart = projection;
        dreamworld$tunnelDir = direction;
        dreamworld$tunnelProgress = 0.0D;
        dreamworld$tunnelLength = remaining;
        boat.noPhysics = true;
        boat.fallDistance = 0.0F;
        boat.setDeltaMovement(Vec3.ZERO);
        boat.setPos(projection.x, projection.y, projection.z);
        boat.setYRot(dreamworld$yawOf(direction));
        if (rider != null) {
            boat.positionRider(rider);
        }
        return true;
    }

    /** How far the rider can reach a tunnel: their block interaction range plus one block. */
    @Unique
    private static double dreamworld$attachReach(AbstractBoat boat) {
        return boat.getControllingPassenger() instanceof Player player
                ? player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 1.0D
                : 0.0D;
    }

    /** {@return whether {@code tunnel} is the line the boat is already riding} */
    @Unique
    private boolean dreamworld$rides(TunnelGeometry.Tunnel tunnel) {
        return dreamworld$tunnelPair != null && dreamworld$tunnelPair.equals(tunnel.pair());
    }

    @Override
    @Nullable
    public TunnelGeometry.Pair currentDreamTunnel() {
        return dreamworld$inTunnel ? dreamworld$tunnelPair : null;
    }

    /** How many tunnels one graffiti may form: the server config, mirrored to the client. */
    @Unique
    private static int dreamworld$tunnelLimit(AbstractBoat boat) {
        return boat.level() instanceof ServerLevel
                ? DreamPlatform.get().config().maxTunnelsPerGraffiti()
                : DreamClientState.maxTunnelsPerGraffiti();
    }

    /**
     * Stops a dream boat from taking on creatures that belong to reality.
     *
     * <p>The two layers share one server world, so an ordinary cow or zombie wandering into the
     * dream sits at the very same coordinates as the boat. Vanilla boards any nearby living entity
     * into a boat that nobody drives, which would put an invisible passenger on a dreamer's boat.
     * A dream boat only takes creatures of its own layer.</p>
     */
    @Redirect(require = 0, method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;"
                            + "startRiding(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean dreamworld$noRealPassengers(Entity passenger, Entity vehicle) {
        if (DreamBoatGate.isDreamBoat((AbstractBoat) (Object) this) && !DreamEntities.isDreamOnly(passenger)) {
            return false;
        }
        return passenger.startRiding(vehicle);
    }

    /**
     * Replaces the boat's own move step while it rides a tunnel.
     *
     * <p>On open ground the vanilla step moves the boat by its whole velocity - thrust, drift and
     * gravity - and correcting that afterwards means the boat is placed twice per tick, which is
     * what made it shiver. Here the step itself is replaced: only the component along the tunnel
     * axis is taken, and the boat is placed exactly on the line in a single move. The velocity that
     * the player built up by rowing is kept, so the next tick's thrust and friction behave exactly
     * like open dream boating.</p>
     */
    @Redirect(require = 0, method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/vehicle/AbstractBoat;"
                            + "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"))
    private void dreamworld$tunnelMove(AbstractBoat boat, MoverType type, Vec3 motion) {
        if (!dreamworld$inTunnel) {
            boat.move(type, motion);
            return;
        }
        double along = motion.dot(dreamworld$tunnelDir);
        dreamworld$tunnelProgress = Mth.clamp(dreamworld$tunnelProgress + along, 0.0D,
                dreamworld$tunnelLength);
        boat.setDeltaMovement(dreamworld$tunnelDir.scale(along));
        Vec3 next = dreamworld$tunnelStart.add(dreamworld$tunnelDir.scale(dreamworld$tunnelProgress));
        boat.setPos(next.x, next.y, next.z);
    }

    /**
     * Turns the boat around inside a tunnel.
     *
     * <p>The tunnel is a line between two graffiti, so reversing folds it onto its other end instead
     * of rebasing it at the boat: the boat becomes the far end, the direction is negated and the
     * progress is mirrored. Rebasing would keep only the distance travelled since the last flip, and
     * three quick presses would shrink the line to nothing - which ends the ride on the spot and
     * drops the boat out of the tunnel.</p>
     *
     * <p>The rider's view is turned too. A tunnel cancels {@code clampRotation} so the player can
     * look around freely, which also means nothing else would follow the boat around - without this
     * the bow turns while the player keeps staring at the wall they just left.</p>
     */
    @Override
    public void reverseDreamTunnel() {
        AbstractBoat boat = (AbstractBoat) (Object) this;
        if (!dreamworld$inTunnel) {
            return;
        }
        Vec3 farEnd = dreamworld$tunnelStart.add(dreamworld$tunnelDir.scale(dreamworld$tunnelLength));
        dreamworld$tunnelStart = farEnd;
        dreamworld$tunnelProgress = dreamworld$tunnelLength - dreamworld$tunnelProgress;
        dreamworld$tunnelDir = dreamworld$tunnelDir.reverse();
        boat.setDeltaMovement(Vec3.ZERO);
        float yaw = dreamworld$yawOf(dreamworld$tunnelDir);
        boat.setYRot(yaw);
        LivingEntity rider = boat.getControllingPassenger();
        if (rider != null) {
            rider.setYRot(yaw);
            // Snap the interpolation origin as well, or the view swings back to the old heading.
            rider.yRotO = yaw;
            rider.setYHeadRot(yaw);
            rider.setYBodyRot(yaw);
            boat.positionRider(rider);
        }
    }

    @Override
    public boolean inDreamTunnel() {
        return dreamworld$inTunnel;
    }

    /**
     * Keeps the boat aimed down the tunnel and leaves at the far end.
     *
     * <p>The movement itself was already handled by {@link #dreamworld$tunnelMove}, and the rider is
     * carried along by vanilla once the boat tick returns, so this only pins the bow and checks the
     * end - placing the boat or the rider a second time here would be exactly the double placement
     * that made the boat shake.</p>
     */
    @Unique
    private void dreamworld$holdTunnel(AbstractBoat boat) {
        boat.fallDistance = 0.0F;
        boat.setYRot(dreamworld$yawOf(dreamworld$tunnelDir));
        if (dreamworld$tunnelProgress >= dreamworld$tunnelLength) {
            dreamworld$finishTunnel(boat);
        }
    }

    /**
     * Leaves the tunnel at its far end.
     *
     * <p>Nothing is thrown anywhere: the boat arrives exactly on the far graffiti and comes to a
     * stop there. The sideways escape belongs to leaving mid-way - using it here flung the boat six
     * blocks off the line and dropped it onto whatever ground could be found below, which is what
     * yanked a rider out of a tunnel that ran under the terrain.</p>
     */
    @Unique
    private void dreamworld$finishTunnel(AbstractBoat boat) {
        dreamworld$inTunnel = false;
        boat.noPhysics = false;
        boat.fallDistance = 0.0F;
        Vec3 end = dreamworld$tunnelStart.add(dreamworld$tunnelDir.scale(dreamworld$tunnelLength));
        boat.setPos(end.x, end.y, end.z);
        boat.setDeltaMovement(Vec3.ZERO);
    }

    /**
     * Drops the boat out of the tunnel, onto the closest landing spot that is far enough away from
     * the tunnel line so the boat is not caught again right away.
     */
    @Unique
    private void dreamworld$leaveTunnel(AbstractBoat boat, boolean eject) {
        if (!dreamworld$inTunnel) {
            return;
        }
        dreamworld$inTunnel = false;
        boat.noPhysics = false;
        Vec3 exit = boat.position();
        double travelled = Mth.clamp(dreamworld$tunnelProgress, 0.0D, dreamworld$tunnelLength);
        Vec3 linePoint = dreamworld$tunnelStart.add(dreamworld$tunnelDir.scale(travelled));
        Vec3 horizontal = new Vec3(exit.x - linePoint.x, 0.0D, exit.z - linePoint.z);
        double distance = horizontal.length();
        Vec3 away;
        if (distance < 0.05D) {
            away = new Vec3(-dreamworld$tunnelDir.z, 0.0D, dreamworld$tunnelDir.x);
            if (away.lengthSqr() < 1.0E-4D) {
                away = new Vec3(1.0D, 0.0D, 0.0D);
            }
            away = away.normalize();
        } else {
            away = horizontal.scale(1.0D / distance);
        }
        Vec3 target = exit;
        if (eject && distance < TUNNEL_ESCAPE_DISTANCE) {
            target = new Vec3(linePoint.x + away.x * TUNNEL_ESCAPE_DISTANCE, exit.y,
                    linePoint.z + away.z * TUNNEL_ESCAPE_DISTANCE);
        }
        double groundY = dreamworld$landingY(boat.level(), target.x, target.y, target.z);
        double y = Double.isNaN(groundY) ? target.y : groundY;
        boat.setPos(target.x, y, target.z);
        boat.fallDistance = 0.0F;
        if (eject) {
            // The push comes off the line sideways, but the bow keeps the tunnel heading: leaving a
            // tunnel should not spin the boat around in the process.
            boat.setDeltaMovement(away.x * 0.25D, 0.15D, away.z * 0.25D);
        } else {
            boat.setDeltaMovement(Vec3.ZERO);
        }
        LivingEntity rider = boat.getControllingPassenger();
        if (rider != null) {
            boat.positionRider(rider);
        }
    }

    /** Graffiti of the dimension the boat is in: the registry on the server, the synced list here. */
    @Unique
    private static List<BlockPos> dreamworld$tunnelAnchors(AbstractBoat boat) {
        if (boat.level() instanceof ServerLevel serverLevel) {
            List<GraffitiRegistry.Entry> entries = GraffitiRegistry.get(serverLevel).entriesIn(serverLevel);
            List<BlockPos> positions = new ArrayList<>(entries.size());
            for (GraffitiRegistry.Entry entry : entries) {
                positions.add(entry.pos());
            }
            return positions;
        }
        return DreamClientState.graffiti();
    }

    /**
     * Finds the tunnel segment closest to {@code point}. Pairs are formed exactly like the client
     * renderer does, so the boat only ever locks onto a tunnel that is actually drawn.
     */
    // ------------------------------------------------------------- helpers

    @Unique
    private static float dreamworld$yawOf(Vec3 direction) {
        return (float) (Mth.atan2(-direction.x, direction.z) * Mth.RAD_TO_DEG);
    }

    /** Top of the closest solid ground below {@code fromY} that leaves room for the boat. */
    @Unique
    private static double dreamworld$landingY(Level level, double x, double fromY, double z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int blockX = Mth.floor(x);
        int blockZ = Mth.floor(z);
        for (int step = 1; step <= LANDING_SCAN_DEPTH; step++) {
            int y = Mth.floor(fromY) - step;
            pos.set(blockX, y, blockZ);
            BlockState state = level.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) {
                continue;
            }
            double top = y + shape.max(Direction.Axis.Y);
            if (dreamworld$isFree(level, x, top + 0.1D, z) && dreamworld$isFree(level, x, top + 1.1D, z)) {
                return top + 0.01D;
            }
        }
        return Double.NaN;
    }

    @Unique
    private static boolean dreamworld$isFree(Level level, double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y, z);
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }
}
