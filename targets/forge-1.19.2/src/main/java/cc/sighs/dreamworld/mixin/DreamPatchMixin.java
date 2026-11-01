package cc.sighs.dreamworld.mixin;

import cc.sighs.dreamworld.dream.DreamPatches;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lowest-level dream patch recorder.
 *
 * <p>{@link LevelChunk#setBlockState} is the single funnel every server-side block change goes
 * through, so hooking it captures everything: player edits, explosions, pistons, commands. The
 * previous state is stored in {@link DreamPatches}, which is what the dream layer reads.</p>
 */
@Mixin(LevelChunk.class)
public abstract class DreamPatchMixin {
    @Inject(method = "setBlockState", at = @At("HEAD"))
    private void dreamworld$recordDreamPatch(BlockPos pos, BlockState state, boolean isMoving,
            CallbackInfoReturnable<BlockState> cir) {
        LevelChunk self = (LevelChunk) (Object) this;
        Level level = self.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockState previous = self.getBlockState(pos);
        if (previous == state || previous.equals(state)) {
            return;
        }
        // Graffiti exists on both sides by design, so it must never end up in the patch set.
        if (previous.getBlock() instanceof cc.sighs.dreamworld.block.DreamGraffitiBlock
                || state.getBlock() instanceof cc.sighs.dreamworld.block.DreamGraffitiBlock) {
            return;
        }
        DreamPatches.get(serverLevel).recordRealEdit(serverLevel, pos, previous);
    }
}
