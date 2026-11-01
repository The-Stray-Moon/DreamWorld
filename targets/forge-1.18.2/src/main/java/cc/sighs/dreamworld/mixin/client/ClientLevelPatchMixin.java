package cc.sighs.dreamworld.mixin.client;

import cc.sighs.dreamworld.client.DreamClientPatches;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the dream overlay on top of server block updates.
 *
 * <p>Every block update the server sends lands in {@link Level#setBlock}; right after it is applied
 * the dream state of that position is painted back, while the real state is remembered so the
 * overlay can be removed when the player wakes up.</p>
 *
 * <p>1.18.2's {@code ClientLevel} does not override {@code setBlock}, so the hook sits on
 * {@link Level} and narrows itself to the client level.</p>
 */
@Mixin(Level.class)
public abstract class ClientLevelPatchMixin {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("RETURN"))
    private void dreamworld$reassertPatch(BlockPos pos, BlockState state, int flags,
            int recursionLeft, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ClientLevel) {
            DreamClientPatches.reassert(pos);
        }
    }
}
