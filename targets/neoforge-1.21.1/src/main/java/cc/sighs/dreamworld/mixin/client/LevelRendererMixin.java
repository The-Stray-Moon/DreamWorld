package cc.sighs.dreamworld.mixin.client;

import cc.sighs.dreamworld.client.DreamClientState;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tints the dream world blocks pink.
 *
 * <p>{@code LevelRenderer#renderSectionLayer} draws every chunk layer (solid, cutout, translucent,
 * ...). The shader colour is multiplied onto the block vertex colours by the chunk shaders, so
 * changing it for the duration of one layer tints exactly the blocks: entities, particles and the
 * HUD are drawn outside of these calls and stay untouched.</p>
 *
 * <p>The colour set before the layer is restored afterwards, which also keeps fancy and fabulous
 * graphics working: fabulous mode only reorders which layers run into which framebuffer, every one
 * of them still goes through this method and is restored.</p>
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    /** Shader colour that was active before the current dream layer was tinted. */
    @Unique
    private float dreamworld$savedRed;
    @Unique
    private float dreamworld$savedGreen;
    @Unique
    private float dreamworld$savedBlue;
    @Unique
    private float dreamworld$savedAlpha;
    @Unique
    private boolean dreamworld$tinted;

    @Inject(method = "renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
            at = @At("HEAD"))
    private void dreamworld$tintBlocks(RenderType type, double x, double y, double z,
            Matrix4f projectionMatrix, Matrix4f modelViewMatrix, CallbackInfo ci) {
        if (!DreamClientState.isDreaming()) {
            return;
        }
        // getShaderColor returns the live array, so copy it before setShaderColor overwrites it.
        float[] current = RenderSystem.getShaderColor();
        this.dreamworld$savedRed = current[0];
        this.dreamworld$savedGreen = current[1];
        this.dreamworld$savedBlue = current[2];
        this.dreamworld$savedAlpha = current[3];
        this.dreamworld$tinted = true;
        // Bright pink, multiplied onto the block vertex colours.
        RenderSystem.setShaderColor(1.0F, 0.55F, 0.75F, 1.0F);
    }

    @Inject(method = "renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
            at = @At("RETURN"))
    private void dreamworld$restoreShaderColor(RenderType type, double x, double y, double z,
            Matrix4f projectionMatrix, Matrix4f modelViewMatrix, CallbackInfo ci) {
        if (!this.dreamworld$tinted) {
            return;
        }
        this.dreamworld$tinted = false;
        RenderSystem.setShaderColor(this.dreamworld$savedRed, this.dreamworld$savedGreen,
                this.dreamworld$savedBlue, this.dreamworld$savedAlpha);
    }
}
