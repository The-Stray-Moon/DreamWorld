package cc.sighs.dreamworld.mixin.client;

import cc.sighs.dreamworld.client.DreamBlockTint;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks the chunk-mesh block batch so the per-quad tint applies to terrain only, never to held or
 * dropped block items that go through the same model renderer.
 */
@Mixin(BlockRenderDispatcher.class)
public abstract class BlockRenderDispatcherMixin {
    @Inject(method = "renderBatched", at = @At("HEAD"))
    private void dreamworld$beginTint(BlockState state, BlockPos pos, BlockAndTintGetter level,
            PoseStack pose, VertexConsumer buffer, boolean checkSides, RandomSource random,
            CallbackInfo ci) {
        DreamBlockTint.begin(state, pos);
    }

    @Inject(method = "renderBatched", at = @At("RETURN"))
    private void dreamworld$endTint(BlockState state, BlockPos pos, BlockAndTintGetter level,
            PoseStack pose, VertexConsumer buffer, boolean checkSides, RandomSource random,
            CallbackInfo ci) {
        DreamBlockTint.end();
    }
}
