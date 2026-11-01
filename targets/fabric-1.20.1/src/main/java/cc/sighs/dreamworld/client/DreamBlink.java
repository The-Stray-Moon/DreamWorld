package cc.sighs.dreamworld.client;

import cc.sighs.dreamworld.DreamWorld;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * The blink when the dream starts or ends.
 *
 * <p>Crossing the mirror is both instantaneous and hard to read: the terrain, the sky and the
 * population swap within one packet. A short blink gives the eyes something to attach the change to,
 * the way closing them does. It is built from two pieces that both ramp with one intensity value -
 * vanilla's own screen blur post chain, applied to the world, and a black overlay on top of
 * everything, HUD included.</p>
 *
 * <p>The blur reuses {@code minecraft:shaders/post/blur.json}, the chain the menus already use, so
 * no custom shader has to be shipped and kept in step with the pipeline.</p>
 */
public final class DreamBlink {
    /** Vanilla's menu blur; the same chain the "blurred background" option uses. */
    private static final ResourceLocation BLUR =
            new ResourceLocation("minecraft", "shaders/post/blur.json");
    /** Ticks spent fading in, and the same again fading out. */
    private static final int FADE_TICKS = 8;
    /** Blur radius at the peak of the blink; vanilla's menu blur tops out around 10. */
    private static final float MAX_BLUR_RADIUS = 8.0F;
    /** Below this the effect is invisible and skipped entirely, so the chain is not run for free. */
    private static final float VISIBLE = 0.01F;

    private static PostChain blur;
    private static int chainWidth = -1;
    private static int chainHeight = -1;
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

    /** Blurs the finished level, before the GUI is drawn on top of it. */
    public static void renderLevel(float partialTick) {
        float intensity = intensity(partialTick);
        if (intensity <= VISIBLE) {
            return;
        }
        PostChain chain = chain();
        if (chain == null) {
            return;
        }
        // 1.20.1 has no runtime uniform setter on PostChain; vanilla's blur chain ships its own
        // radius, so the fade only decides whether the chain runs at all.
        chain.process(partialTick);
    }

    /** The black frame on top of everything, HUD included. */
    public static void renderGui(GuiGraphics graphics) {
        float partialTick = Minecraft.getInstance().getFrameTime();
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

    /**
     * The blur chain, created on first use and resized with the window.
     *
     * <p>Failure is not fatal: a blink without blur still reads as a blink, so a missing or broken
     * shader only leaves the black fade.</p>
     */
    @SuppressWarnings("null")
    private static PostChain chain() {
        Minecraft minecraft = Minecraft.getInstance();
        int width = minecraft.getWindow().getWidth();
        int height = minecraft.getWindow().getHeight();
        if (blur == null) {
            try {
                blur = new PostChain(minecraft.getTextureManager(), minecraft.getResourceManager(),
                        minecraft.getMainRenderTarget(), BLUR);
                blur.resize(width, height);
                chainWidth = width;
                chainHeight = height;
            } catch (IOException | RuntimeException exception) {
                blur = null;
                return null;
            }
        } else if (width != chainWidth || height != chainHeight) {
            blur.resize(width, height);
            chainWidth = width;
            chainHeight = height;
        }
        return blur;
    }

    /** Drops the chain on a resource reload; the shaders behind it have been replaced. */
    public static SimplePreparableReloadListener<Void> reloadListener() {
        return new SimplePreparableReloadListener<Void>() {
            @Override
            protected Void prepare(ResourceManager manager, ProfilerFiller profiler) {
                return null;
            }

            @Override
            protected void apply(Void value, ResourceManager manager, ProfilerFiller profiler) {
                if (blur != null) {
                    blur.close();
                    blur = null;
                    chainWidth = -1;
                    chainHeight = -1;
                }
            }
        };
    }
}
