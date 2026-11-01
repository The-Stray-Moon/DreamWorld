package cc.sighs.dreamworld.mixin;

import cc.sighs.dreamworld.dream.DreamIntrusion;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Makes the server mine the block the dreamer actually sees.
 *
 * <p>The dream layer only exists in the patch set: a dreamer's client walks on dream blocks while
 * the server still holds the real chunk. Vanilla mining reads the real state, so a dream block
 * without a counterpart in reality looks like air - the server never starts or finishes the break,
 * {@code BlockEvent.BreakEvent} never fires and nothing is carried into reality. Every state read of
 * the mining path is therefore routed through {@link DreamIntrusion#stateForMining}, which answers
 * the dream state for a dreaming player holding dream alloy gear and the untouched real state for
 * everybody else.</p>
 *
 * <p>The counts are spelled out per method: {@code @Redirect} requires exactly one match per method
 * by default, so a shared injection across these three methods would fail to apply at all. They are
 * also a guard - if a future vanilla changes how often the mining path reads a state, the mixin
 * refuses to load instead of silently mining the wrong layer.</p>
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class DreamMiningMixin {
    @Shadow
    @Final
    protected ServerPlayer player;

    /** Two reads: the delayed destroy target and the block currently being destroyed. */
    @Redirect(
            method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState dreamworld$mineDreamStateWhileTicking(ServerLevel level, BlockPos pos) {
        return DreamIntrusion.stateForMining(this.player, level, pos);
    }

    /**
     * Six reads: three correction packets, the start-of-break state, the abort correction and the
     * stop-of-break state. The corrections matter too - they are sent to the breaker, so they must
     * carry the layer that player sees or the dream block flickers back to the real state.
     */
    @Redirect(
            method = "handleBlockBreakAction",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState dreamworld$mineDreamStateOnAction(ServerLevel level, BlockPos pos) {
        return DreamIntrusion.stateForMining(this.player, level, pos);
    }

    /** One read: the state handed to the break event and the harvest logic. */
    @Redirect(
            method = "destroyBlock",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState dreamworld$mineDreamStateOnDestroy(ServerLevel level, BlockPos pos) {
        return DreamIntrusion.stateForMining(this.player, level, pos);
    }
}
