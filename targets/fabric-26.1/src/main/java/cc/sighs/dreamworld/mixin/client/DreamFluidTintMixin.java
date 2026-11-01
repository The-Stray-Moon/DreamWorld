package cc.sighs.dreamworld.mixin.client;

import cc.sighs.dreamworld.client.DreamBlockTintSource;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Wraps the fluid model's own tint source so water and lava use the dream palette too. */
@Mixin(FluidRenderer.class)
public abstract class DreamFluidTintMixin {
    @Redirect(method = "tesselate",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/block/FluidModel;tintSource()"
                            + "Lnet/minecraft/client/color/block/BlockTintSource;"))
    private BlockTintSource dreamworld$tintFluid(FluidModel model) {
        return new DreamBlockTintSource(model.tintSource(), true);
    }
}
