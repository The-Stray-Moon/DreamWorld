package cc.sighs.dreamworld.mixin.client;

import cc.sighs.dreamworld.client.DreamBlockTint;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
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
    private static final String RENDER_BATCHED =
            "renderBatched(Lnet/minecraft/world/level/block/state/BlockState;"
                    + "Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/BlockAndTintGetter;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lcom/mojang/blaze3d/vertex/VertexConsumer;"
                    + "ZLnet/minecraft/util/RandomSource;"
                    + "Lnet/neoforged/neoforge/client/model/data/ModelData;"
                    + "Lnet/minecraft/client/renderer/RenderType;)V";

    @Inject(method = RENDER_BATCHED, at = @At("HEAD"))
    private void dreamworld$beginTint(BlockState state, BlockPos pos, BlockAndTintGetter level,
            PoseStack pose, VertexConsumer buffer, boolean checkSides, RandomSource random,
            ModelData modelData, RenderType renderType, CallbackInfo ci) {
        DreamBlockTint.begin(state);
    }

    @Inject(method = RENDER_BATCHED, at = @At("RETURN"))
    private void dreamworld$endTint(BlockState state, BlockPos pos, BlockAndTintGetter level,
            PoseStack pose, VertexConsumer buffer, boolean checkSides, RandomSource random,
            ModelData modelData, RenderType renderType, CallbackInfo ci) {
        DreamBlockTint.end();
    }
}
