package cc.sighs.dreamworld.mixin.client;

import cc.sighs.dreamworld.client.DreamClientPatches;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the dream overlay on top of server block updates.
 *
 * <p>Every block update the server sends lands in {@link ClientLevel#setBlock}; right after it is
 * applied the dream state of that position is painted back, while the real state is remembered so
 * the overlay can be removed when the player wakes up.</p>
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelPatchMixin {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("RETURN"))
    private void dreamworld$reassertPatch(BlockPos pos, BlockState state, int flags, int recursionLeft,
            CallbackInfoReturnable<Boolean> cir) {
        DreamClientPatches.reassert(pos);
    }

    /**
     * Server correction packets bypass {@link ClientLevel#setBlock} and go straight to
     * {@code super.setBlock} through this method, so the overlay has to be put back here as well.
     * Without it, a use packet aimed at a virtual dream block (the sleep gesture is deliberately
     * allowed through) lets the server answer with air and the dream block disappears until the
     * next patch resend.
     */
    @Inject(method = "setServerVerifiedBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)V",
            at = @At("RETURN"))
    private void dreamworld$reassertVerifiedPatch(BlockPos pos, BlockState state, int flags,
            CallbackInfo ci) {
        DreamClientPatches.reassert(pos);
    }
}
