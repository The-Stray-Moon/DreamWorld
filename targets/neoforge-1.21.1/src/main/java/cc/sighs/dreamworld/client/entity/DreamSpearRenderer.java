package cc.sighs.dreamworld.client.entity;

import cc.sighs.dreamworld.client.DreamClientState;
import cc.sighs.dreamworld.entity.DreamSpearEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * Renderer for the thrown dream alloy spear.
 *
 * <p>The spear has no model of its own, so the flat item sprite stands in for the trident's model.
 * The orientation frame is the one {@code ThrownTridentRenderer} uses - the pole axis follows the
 * flight direction - and the sprite is then turned onto that axis and scaled up, so a thrown spear
 * reads as a long weapon instead of a floating icon.</p>
 *
 * <p>The spear also belongs to the layer it was thrown on. On the other layer it is drawn as a pale
 * blue ghost, unlit and translucent, and cannot be picked up.</p>
 */
public class DreamSpearRenderer extends EntityRenderer<DreamSpearEntity> {
    /** The trident model is about two blocks long; the 16px diagonal needs this much to match it. */
    private static final float SPEAR_SCALE = 1.4F;
    /**
     * Turns the sprite's diagonal onto the pole axis of the trident model.
     *
     * <p>The sprite runs from the bottom-left corner to the tip in the top-right corner, i.e. 45
     * degrees in the item plane. The trident model's head sits on its local -Y axis, and that is the
     * axis the orientation frame above points along the flight, so the diagonal has to land on -Y:
     * 45 - 135 = -90 degrees.</p>
     */
    private static final float SPRITE_TILT = -135.0F;
    /** Pale blue of the ghost; the same family as the graffiti tunnels. */
    private static final float GHOST_RED = 0.62F;
    private static final float GHOST_GREEN = 0.85F;
    private static final float GHOST_BLUE = 1.0F;
    private static final float GHOST_ALPHA = 0.35F;

    private final ItemRenderer itemRenderer;

    public DreamSpearRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(DreamSpearEntity spear, float yaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight) {
        poseStack.pushPose();
        // The trident's frame: the model pole runs along the flight direction. Reusing it keeps the
        // spear pointing where it actually travels instead of being billboarded at the camera.
        poseStack.mulPose(Axis.YP.rotationDegrees(
                Mth.lerp(partialTick, spear.yRotO, spear.getYRot()) - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(
                Mth.lerp(partialTick, spear.xRotO, spear.getXRot()) + 90.0F));
        // The sprite is drawn diagonally across its canvas; turn it onto the pole axis.
        poseStack.mulPose(Axis.ZP.rotationDegrees(SPRITE_TILT));
        poseStack.scale(SPEAR_SCALE, SPEAR_SCALE, SPEAR_SCALE);
        boolean ghost = isGhost(spear);
        itemRenderer.renderStatic(spear.getItem(), ItemDisplayContext.NONE, packedLight,
                OverlayTexture.NO_OVERLAY, poseStack, new SpearBuffer(buffers, ghost),
                spear.level(), spear.getId());
        poseStack.popPose();
        super.render(spear, yaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(DreamSpearEntity spear) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    /**
     * {@return whether the spear sits on the other layer than the local player}
     *
     * <p>{@link DreamClientState#isDreaming()} is the layer the client currently shows, which is the
     * layer the local player stands on. A spear with the same value is solid; any other combination
     * is a ghost.</p>
     */
    public static boolean isGhost(DreamSpearEntity spear) {
        return spear.dreamSide() != DreamClientState.isDreaming();
    }

    /**
     * Buffer that makes the spear readable from both sides, and pale blue when it is a ghost.
     *
     * <p>Item models are normally drawn with a culling cutout sheet, which would make the sprite
     * vanish as soon as the spear is viewed from its back face - guaranteed to happen at some point
     * once the plane follows the flight direction. Both sheets below are non-culling and live on the
     * block atlas, so the model's UVs stay valid.</p>
     */
    private record SpearBuffer(MultiBufferSource wrapped, boolean ghost) implements MultiBufferSource {
        private static final RenderType SOLID =
                RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS);
        private static final RenderType GHOST =
                RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS);

        @Override
        public VertexConsumer getBuffer(RenderType ignored) {
            VertexConsumer buffer = wrapped.getBuffer(ghost ? GHOST : SOLID);
            return ghost ? new GhostVertexConsumer(buffer) : buffer;
        }
    }

    /** Rewrites every vertex of the ghost: pale blue, translucent and unlit. */
    private record GhostVertexConsumer(VertexConsumer wrapped) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            wrapped.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            wrapped.setColor(GHOST_RED, GHOST_GREEN, GHOST_BLUE, GHOST_ALPHA);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            wrapped.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            wrapped.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            // Full brightness: a projection does not sit in the world's light.
            wrapped.setUv2(LightTexture.FULL_BRIGHT & 0xFFFF, LightTexture.FULL_BRIGHT >> 16);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            wrapped.setNormal(x, y, z);
            return this;
        }
    }
}
