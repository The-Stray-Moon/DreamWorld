package cc.sighs.dreamworld.mixin.client;

import cc.sighs.dreamworld.client.DreamBoatControls;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Forwards key presses to the dream boat controls. */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"))
    private void dreamworld$keyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
        DreamBoatControls.onKey(event, action);
    }
}
