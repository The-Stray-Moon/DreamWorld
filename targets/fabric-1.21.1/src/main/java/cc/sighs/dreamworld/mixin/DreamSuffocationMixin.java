package cc.sighs.dreamworld.mixin;

import cc.sighs.dreamworld.dream.DreamManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stops the server from suffocating a dreamer with blocks that only exist in reality.
 *
 * <p>A dreamer's body never leaves the real level - the dream layer is a client-side view - and its
 * position is the one the client reports (see {@code DreamMoveMixin}). The two worlds do not line up:
 * the dream keeps the terrain as it was generated, so a block the dreamer sees as air can still be
 * there in reality. Falling into such a spot puts the server-side eye inside the real block, and
 * {@code LivingEntity#baseTick} would then deal one point of in-wall damage per tick until death,
 * while the client has nothing to push the player out of.</p>
 *
 * <p>{@code LivingEntity#isInWall} already exempts sleepers, but a dreamer is not sleeping at all:
 * the sleeping look exists only on the observers' clients, while the server player keeps a normal
 * standing pose and no sleeping position. The exemption is therefore extended to dreamers
 * explicitly. Collision, gravity and falling stay untouched - only the real-world suffocation check
 * is skipped.</p>
 */
@Mixin(LivingEntity.class)
public abstract class DreamSuffocationMixin {
    @Inject(method = "isInWall", at = @At("HEAD"), cancellable = true)
    private void dreamworld$noSuffocationInDream(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayer player && DreamManager.isDreaming(player)) {
            cir.setReturnValue(false);
        }
    }
}
