package cc.sighs.dreamworld.mixin.client;

import cc.sighs.dreamworld.client.DreamClientState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides the sun on the dream layer; the sky is pinned to a permanent noon. */
@Mixin(SkyRenderer.class)
public abstract class DreamSunMixin {
    @Inject(method = "renderSun", at = @At("HEAD"), cancellable = true)
    private void dreamworld$hideSun(float size, PoseStack pose, CallbackInfo ci) {
        if (DreamClientState.isDreaming()) {
            ci.cancel();
        }
    }
}
