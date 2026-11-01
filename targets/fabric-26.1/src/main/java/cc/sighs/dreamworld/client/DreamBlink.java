package cc.sighs.dreamworld.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * The blink when the dream starts or ends.
 *
 * <p>Crossing the mirror is both instantaneous and hard to read: the terrain, the sky and the
 * population swap within one packet. A short black fade gives the eyes something to attach the
 * change to, the way closing them does. 26.1 drives post chains through the frame graph, so the
 * screen blur of the 1.21 family is not reimplemented here; the fade carries the effect.</p>
 */
public final class DreamBlink {
    /** Ticks spent fading in, and the same again fading out. */
    private static final int FADE_TICKS = 8;
    /** Below this the effect is invisible and skipped entirely. */
    private static final float VISIBLE = 0.01F;

    private static int remaining;
    private static float amount;
    private static float lastAmount;

    private DreamBlink() {
    }

    /** Starts the blink. Called on both entries, so entering and leaving look the same. */
    public static void trigger() {
        remaining = FADE_TICKS * 2;
        amount = 0.0F;
        lastAmount = 0.0F;
    }

    /** {@return whether a blink is currently running} */
    public static boolean active() {
        return remaining > 0;
    }

    public static void tick() {
        lastAmount = amount;
        if (remaining <= 0) {
            amount = 0.0F;
            return;
        }
        remaining--;
        // Zero at both ends of the animation, one in the middle.
        float progress = 1.0F - (float) remaining / (FADE_TICKS * 2.0F);
        amount = 1.0F - Math.abs(progress * 2.0F - 1.0F);
    }

    /** The black frame on top of everything, HUD included. */
    public static void renderGui(GuiGraphicsExtractor graphics) {
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float intensity = intensity(partialTick);
        if (intensity <= 0.0F) {
            return;
        }
        int alpha = Mth.clamp((int) (intensity * 255.0F), 0, 255) << 24;
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alpha);
    }

    private static float intensity(float partialTick) {
        if (remaining <= 0 && amount <= 0.0F) {
            return 0.0F;
        }
        return Mth.lerp(partialTick, lastAmount, amount);
    }

}
