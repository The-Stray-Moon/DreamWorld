package cc.sighs.dreamworld.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * The blink when the dream starts or ends.
 *
 * <p>Crossing the mirror is both instantaneous and hard to read: the terrain, the sky and the
 * population swap within one packet. A short blink gives the eyes something to attach the change to,
 * the way closing them does.</p>
 *
 * <p>1.18.2 exposes neither a post-chain uniform setter nor {@code GuiGraphics}, so the blink is the
 * black overlay alone: the vanilla blur chain cannot have its radius driven from outside. The
 * overlay still fades in and out and is drawn in the same place as the 1.21.1 target.</p>
 */
public final class DreamBlink {
    /** Ticks spent fading in, and the same again fading out. */
    private static final int FADE_TICKS = 8;

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

    /** 1.18.2 has no usable post-chain radius, so the world pass has nothing to do. */
    public static void renderLevel(float partialTick) {
    }

    /** The black frame on top of everything, HUD included. */
    public static void renderGui(PoseStack poseStack) {
        float partialTick = Minecraft.getInstance().getFrameTime();
        float intensity = intensity(partialTick);
        if (intensity <= 0.0F) {
            return;
        }
        int alpha = Mth.clamp((int) (intensity * 255.0F), 0, 255) << 24;
        Minecraft minecraft = Minecraft.getInstance();
        GuiComponent.fill(poseStack, 0, 0, minecraft.getWindow().getGuiScaledWidth(),
                minecraft.getWindow().getGuiScaledHeight(), alpha);
    }

    private static float intensity(float partialTick) {
        if (remaining <= 0 && amount <= 0.0F) {
            return 0.0F;
        }
        return Mth.lerp(partialTick, lastAmount, amount);
    }

    /** Kept for the target's reload listener hook; there is no post chain to drop. */
    public static SimplePreparableReloadListener<Void> reloadListener() {
        return new SimplePreparableReloadListener<Void>() {
            @Override
            protected Void prepare(ResourceManager manager, ProfilerFiller profiler) {
                return null;
            }

            @Override
            protected void apply(Void value, ResourceManager manager, ProfilerFiller profiler) {
                // Nothing cached.
            }
        };
    }
}
