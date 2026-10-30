package cc.sighs.dreamworld.mixin;

import cc.sighs.dreamworld.dream.DreamManager;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Trusts the client while it is dreaming.
 *
 * <p>The dream layer exists only on the client: it walks on the patched terrain and collides with
 * the dream blocks, while the server still holds the real chunks. Vanilla movement validation
 * rejects exactly that desync ("moved wrongly" and the collision rubber-band). For a dreaming
 * player the packet position is therefore taken as-is; the server never re-simulates that walk.</p>
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class DreamMoveMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    private void dreamworld$trustDreamMovement(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
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
        ci.cancel();
    }
}
