package cc.sighs.dreamworld.mixin;

import cc.sighs.dreamworld.client.DreamClientState;
import cc.sighs.dreamworld.boat.TunnelGeometry;
import cc.sighs.dreamworld.dream.DreamManager;
import cc.sighs.dreamworld.dream.GraffitiRegistry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Dream boat: a boat ridden by a dreaming player slides over every block like over blue ice,
 * can jump, climbs walls and locks onto the graffiti tunnels.
 *
 * <p>All effects are gated on the controlling passenger being a dreaming player, so ordinary
 * boating is untouched. The boat is simulated by whichever side controls it (the owning client
 * for the rider, the server for the same boat), so the gate has to work on both sides.</p>
 */
@Mixin(Boat.class)
public abstract class BoatDreamMixin {
    /** Slipperiness of blue ice: 98.9% of the horizontal speed survives each tick. */
    private static final float DREAM_FRICTION = 0.989F;
    /** Extra forward push per tick on top of the vanilla paddling force. */
    private static final double DREAM_EXTRA_THRUST = 0.008D;
    /** Largest turning impulse per tick; blue ice otherwise makes the boat spin out. */
    private static final float DREAM_MAX_TURN = 3.0F;
    /** Upward speed added per tick while the boat grinds against a wall. */
    private static final double DREAM_CLIMB_SPEED = 0.12D;
    /** Upper bound of the climbing speed. */
    private static final double DREAM_CLIMB_MAX = 0.5D;
    /** Vertical impulse of a dream boat jump. */
    private static final double DREAM_JUMP_POWER = 0.5D;
    /** Cooldown between two jumps, in ticks. */
    private static final int DREAM_JUMP_COOLDOWN = 8;
    /** Distance at which a moving boat locks onto a graffiti tunnel. */
    private static final double TUNNEL_ATTACH_RADIUS = 4.0D;
    /** Blocks travelled per tick inside a tunnel. */
    private static final double TUNNEL_SPEED = 2.0D;
    /** Distance the boat is pushed away from a tunnel when it leaves one. */
    private static final double TUNNEL_ESCAPE_DISTANCE = 6.0D;
    /** Ticks during which a boat cannot lock onto a tunnel again after leaving one. */
    private static final int TUNNEL_REATTACH_DELAY = 40;
    /** Same budget as the client renderer, keeps the boat on a tunnel that is actually drawn. */
    private static final int TUNNEL_MAX_PAIRS = 64;
    /** How deep the boat looks for a place to land, in blocks. */
    private static final int LANDING_SCAN_DEPTH = 48;

    @Shadow
    private float deltaRotation;

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
    private int dreamworld$reattachDelay;
    @Unique
    private int dreamworld$jumpCooldown;
    @Unique
    private boolean dreamworld$wasJumping;
    // ------------------------------------------------------------- dream gate

    /** A boat is a dream boat only while a dreaming player controls it. */
    @Unique
    private static boolean dreamworld$isDreamRider(@Nullable Entity rider) {
        if (!(rider instanceof Player player)) {
            return false;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            return DreamManager.isDreaming(serverPlayer);
        }
        // The owning client simulates the boat, so it needs the same answer; DreamClientState only
        // holds the flag that DreamManager mirrors to this client and has no client-only imports.
        return player.isLocalPlayer() && DreamClientState.isDreaming();
    }

    // ------------------------------------------------------------- blue ice speed

    @Inject(method = "getGroundFriction", at = @At("RETURN"), cancellable = true)
    private void dreamworld$iceFriction(CallbackInfoReturnable<Float> cir) {
        if (!dreamworld$isDreamRider(((Boat) (Object) this).getControllingPassenger())) {
            return;
        }
        float friction = cir.getReturnValueF();
        if (Float.isNaN(friction)) {
            // Nothing below the boat: keep the vanilla IN_AIR state.
            return;
        }
        cir.setReturnValue(Math.max(friction, DREAM_FRICTION));
    }

    @Inject(method = "floatBoat", at = @At("TAIL"))
    private void dreamworld$extraThrust(CallbackInfo ci) {
        Boat boat = (Boat) (Object) this;
        if (dreamworld$inTunnel || boat.isInWater()) {
            return;
        }
        if (!dreamworld$isDreamRider(boat.getControllingPassenger())) {
            return;
        }
        if (!boat.getPaddleState(Boat.PADDLE_LEFT) && !boat.getPaddleState(Boat.PADDLE_RIGHT)) {
            return;
        }
        float yaw = boat.getYRot() * Mth.DEG_TO_RAD;
        boat.setDeltaMovement(boat.getDeltaMovement().add(
                -Mth.sin(yaw) * DREAM_EXTRA_THRUST, 0.0D, Mth.cos(yaw) * DREAM_EXTRA_THRUST));
    }

    @Inject(method = "controlBoat", at = @At("TAIL"))
    private void dreamworld$clampTurn(CallbackInfo ci) {
        if (!dreamworld$isDreamRider(((Boat) (Object) this).getControllingPassenger())) {
            return;
        }
        this.deltaRotation = Mth.clamp(this.deltaRotation, -DREAM_MAX_TURN, DREAM_MAX_TURN);
    }

    // ------------------------------------------------------------- jump / climb / tunnel

    @Inject(method = "tick", at = @At("TAIL"))
    private void dreamworld$dreamTick(CallbackInfo ci) {
        Boat boat = (Boat) (Object) this;
        if (dreamworld$reattachDelay > 0) {
            dreamworld$reattachDelay--;
        }
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
        if (!boat.isControlledByLocalInstance()) {
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
                dreamworld$advanceTunnel(boat);
                return;
            }
        }
        if (jumpPressed && dreamworld$jumpCooldown == 0 && (boat.onGround() || boat.isInWater())) {
            Vec3 delta = boat.getDeltaMovement();
            boat.setDeltaMovement(delta.x, DREAM_JUMP_POWER, delta.z);
            dreamworld$jumpCooldown = DREAM_JUMP_COOLDOWN;
            return;
        }
        dreamworld$climbWall(boat);
        if (dreamworld$reattachDelay == 0) {
            dreamworld$tryAttachTunnel(boat, rider);
        }
    }

    @Unique
    private void dreamworld$climbWall(Boat boat) {
        if (!boat.horizontalCollision) {
            return;
        }
        Vec3 delta = boat.getDeltaMovement();
        double horizontalSpeedSqr = delta.x * delta.x + delta.z * delta.z;
        if (horizontalSpeedSqr < 0.0025D || delta.y >= DREAM_CLIMB_MAX) {
            return;
        }
        boat.setDeltaMovement(delta.x, Math.min(delta.y + DREAM_CLIMB_SPEED, DREAM_CLIMB_MAX), delta.z);
    }

    // ------------------------------------------------------------- graffiti tunnels

    /** Snaps the boat onto the closest graffiti tunnel when it drives into one. */
    @Unique
    private void dreamworld$tryAttachTunnel(Boat boat, @Nullable LivingEntity rider) {
        if (!boat.getPaddleState(Boat.PADDLE_LEFT) && !boat.getPaddleState(Boat.PADDLE_RIGHT)) {
            // Only a boat being driven is caught, a parked boat stays parked.
            return;
        }
        List<BlockPos> anchors = dreamworld$tunnelAnchors(boat);
        if (anchors.size() < 2) {
            return;
        }
        Vec3 position = boat.position();
        TunnelGeometry.Tunnel tunnel = TunnelGeometry.nearest(anchors, position);
        if (tunnel == null || tunnel.distanceSq() > TUNNEL_ATTACH_RADIUS * TUNNEL_ATTACH_RADIUS) {
            return;
        }
        double length = tunnel.length();
        if (length < 2.0D) {
            return;
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
            return;
        }
        Vec3 projection = tunnel.projection();
        dreamworld$inTunnel = true;
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
    }

    @Unique
    private void dreamworld$advanceTunnel(Boat boat) {
        dreamworld$tunnelProgress += TUNNEL_SPEED;
        boat.fallDistance = 0.0F;
        boat.setDeltaMovement(Vec3.ZERO);
        if (dreamworld$tunnelProgress >= dreamworld$tunnelLength) {
            Vec3 end = dreamworld$tunnelStart.add(dreamworld$tunnelDir.scale(dreamworld$tunnelLength));
            boat.setPos(end.x, end.y, end.z);
            dreamworld$leaveTunnel(boat, true);
            return;
        }
        Vec3 next = dreamworld$tunnelStart.add(dreamworld$tunnelDir.scale(dreamworld$tunnelProgress));
        boat.setPos(next.x, next.y, next.z);
        boat.setYRot(dreamworld$yawOf(dreamworld$tunnelDir));
        LivingEntity rider = boat.getControllingPassenger();
        if (rider != null) {
            boat.positionRider(rider);
        }
    }

    /**
     * Drops the boat out of the tunnel, onto the closest landing spot that is far enough away from
     * the tunnel line so the boat is not caught again right away.
     */
    @Unique
    private void dreamworld$leaveTunnel(Boat boat, boolean eject) {
        if (!dreamworld$inTunnel) {
            return;
        }
        dreamworld$inTunnel = false;
        dreamworld$reattachDelay = TUNNEL_REATTACH_DELAY;
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
            boat.setDeltaMovement(away.x * 0.25D, 0.15D, away.z * 0.25D);
            boat.setYRot(dreamworld$yawOf(away));
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
    private static List<BlockPos> dreamworld$tunnelAnchors(Boat boat) {
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
