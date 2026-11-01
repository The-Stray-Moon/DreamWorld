package cc.sighs.dreamworld.mixin;

import cc.sighs.dreamworld.dream.DreamEntities;
import cc.sighs.dreamworld.dream.DreamManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps dream-side creatures out of the tracking sets of players that are not dreaming.
 *
 * <p>{@code ChunkMap} asks this before sending the spawn packet, so a real-world player never
 * receives the dream copies; entering the dream re-evaluates the set and sends them.</p>
 */
@Mixin(Entity.class)
public abstract class DreamEntityTrackingMixin {
    @Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true)
    private void dreamworld$hideDreamEntities(ServerPlayer player,
            CallbackInfoReturnable<Boolean> cir) {
        if (DreamEntities.isDreamOnly((Entity) (Object) this) && !DreamManager.isDreaming(player)) {
            cir.setReturnValue(false);
        }
    }
}
