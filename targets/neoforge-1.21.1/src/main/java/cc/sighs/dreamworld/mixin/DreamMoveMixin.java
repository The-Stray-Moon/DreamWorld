package cc.sighs.dreamworld.mixin;

import cc.sighs.dreamworld.boat.DreamBoatGate;
import cc.sighs.dreamworld.dream.DreamManager;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Trusts the client for dream movement, on foot and in a boat.
 *
 * <p>The dream layer exists only on the client: it walks on the patched terrain and collides with
 * the dream blocks, while the server still holds the real chunks. Vanilla movement validation
 * rejects exactly that desync ("moved wrongly", the collision rubber-band), so for a dreaming
 * player the packet position is taken as-is and the server never re-simulates that walk.</p>
 *
 * <p>Cancelling the whole handler also throws away its bookkeeping, and the chunk re-tracking is part
 * of that: {@code ServerChunkCache#move} is what notices the player entered a new chunk and sends
 * the chunks around them. Without it the server kept the dreamer in the chunk they started in, so
 * walking or riding far away left the client with unloaded terrain. That one call is therefore made
 * here.</p>
 *
 * <p>The boat case has a second reason to be trusted. A graffiti tunnel runs straight through
 * terrain, so the vehicle packet describes a boat that is inside blocks as far as reality is
 * concerned. Vanilla would reject every one of those moves and answer with a
 * {@code ClientboundMoveVehiclePacket} at the old position - the boat rubber-banded back to where it
 * entered the tunnel, and the rider was pinned to it because the boat kept being yanked away.</p>
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class DreamMoveMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    private void dreamworld$trustDreamMovement(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        // Vanilla's own first line, and it has to stay first here too: without it this handler runs
        // on the netty thread while the server ticks the same chunk map, which has already killed a
        // connection with a ConcurrentModificationException.
        PacketUtils.ensureRunningOnSameThread(packet, (ServerGamePacketListenerImpl) (Object) this,
                this.player.serverLevel());
        if (!DreamManager.isDreaming(this.player)) {
            return;
        }
        this.player.absMoveTo(
                packet.getX(this.player.getX()),
                packet.getY(this.player.getY()),
                packet.getZ(this.player.getZ()),
                packet.getYRot(this.player.getYRot()),
                packet.getXRot(this.player.getXRot()));
        this.player.setOnGround(packet.isOnGround());
        dreamworld$retrack();
        ci.cancel();
    }

    @Inject(method = "handleMoveVehicle", at = @At("HEAD"), cancellable = true)
    private void dreamworld$trustDreamVehicle(ServerboundMoveVehiclePacket packet, CallbackInfo ci) {
        PacketUtils.ensureRunningOnSameThread(packet, (ServerGamePacketListenerImpl) (Object) this,
                this.player.serverLevel());
        Entity root = this.player.getRootVehicle();
        if (!(root instanceof Boat boat) || !DreamBoatGate.isDreamBoat(boat)) {
            return;
        }
        // An invalid packet falls through to vanilla, which is the one that knows how to disconnect.
        if (!Double.isFinite(packet.getX()) || !Double.isFinite(packet.getY())
                || !Double.isFinite(packet.getZ()) || !Float.isFinite(packet.getYRot())
                || !Float.isFinite(packet.getXRot())) {
            return;
        }
        boat.absMoveTo(packet.getX(), packet.getY(), packet.getZ(), packet.getYRot(),
                packet.getXRot());
        boat.positionRider(this.player);
        dreamworld$retrack();
        ci.cancel();
    }

    /** Re-runs the chunk tracking the cancelled handlers would have done. */
    private void dreamworld$retrack() {
        if (this.player.level() instanceof ServerLevel level) {
            level.getChunkSource().move(this.player);
        }
    }
}
