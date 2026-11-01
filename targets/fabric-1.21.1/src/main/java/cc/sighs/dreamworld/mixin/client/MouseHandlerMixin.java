package cc.sighs.dreamworld.mixin.client;

import cc.sighs.dreamworld.client.DreamBoatControls;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Forwards mouse presses to the dream boat controls before vanilla handles the click. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onPress", at = @At("HEAD"))
    private void dreamworld$onPress(long window, int button, int action, int mods, CallbackInfo ci) {
        DreamBoatControls.onMouseButton(button, action);
    }
}
