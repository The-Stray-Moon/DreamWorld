package cc.sighs.dreamworld.mixin.client;

import cc.sighs.dreamworld.client.DreamBoatControls;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Forwards mouse presses to the dream boat controls before vanilla handles the click. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onButton", at = @At("HEAD"))
    private void dreamworld$onButton(long window, MouseButtonInfo button, int action,
            CallbackInfo ci) {
        DreamBoatControls.onMouseButton(button.button(), action);
    }
}
