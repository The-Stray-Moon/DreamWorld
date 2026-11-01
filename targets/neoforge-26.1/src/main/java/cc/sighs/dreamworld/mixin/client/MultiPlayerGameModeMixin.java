package cc.sighs.dreamworld.mixin.client;

import cc.sighs.dreamworld.dream.DreamWorldRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stops block breaking on the client before any prediction happens, so a dreamer's client never
 * starts damaging a block it is not allowed to break. The server repeats the same rule.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void dreamworld$blockStartDestroy(BlockPos pos, Direction direction,
            CallbackInfoReturnable<Boolean> cir) {
        if (dreamworld$blocked(pos)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void dreamworld$blockContinueDestroy(BlockPos pos, Direction direction,
            CallbackInfoReturnable<Boolean> cir) {
        if (dreamworld$blocked(pos)) {
            cir.setReturnValue(false);
        }
    }

    private static boolean dreamworld$blocked(BlockPos pos) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !DreamWorldRules.isDreaming(player)) {
            return false;
        }
        return !DreamWorldRules.mayBreak(player, player.level().getBlockState(pos));
    }
}
