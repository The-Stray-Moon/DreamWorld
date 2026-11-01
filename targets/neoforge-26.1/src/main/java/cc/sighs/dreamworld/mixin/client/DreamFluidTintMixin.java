package cc.sighs.dreamworld.mixin.client;

import cc.sighs.dreamworld.client.DreamFluidTintSource;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.neoforged.neoforge.client.fluid.FluidTintSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Wraps NeoForge's fluid tint source so water and lava use the dream palette too. */
@Mixin(FluidRenderer.class)
public abstract class DreamFluidTintMixin {
    @Redirect(method = "tesselate",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/block/FluidModel;fluidTintSource()"
                            + "Lnet/neoforged/neoforge/client/fluid/FluidTintSource;"))
    private FluidTintSource dreamworld$tintFluid(FluidModel model) {
        return new DreamFluidTintSource(model.fluidTintSource());
    }
}
