package cc.sighs.dreamworld.client.entity;

import cc.sighs.dreamworld.client.DreamClientState;
import cc.sighs.dreamworld.entity.DreamSpearEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

/**
 * Renderer for the thrown dream alloy spear.
 *
 * <p>The spear has no model of its own, so the flat item sprite stands in for the trident's model.
 * The orientation frame is the one {@code ThrownTridentRenderer} uses - the pole axis follows the
 * flight direction - and the sprite is then turned onto that axis and scaled up, so a thrown spear
 * reads as a long weapon instead of a floating icon.</p>
 *
 * <p>26.1 draws items through a render state submitted into the level's {@link SubmitNodeCollector},
 * so the palette rewrite of the 1.21 ghost buffer is gone: the spear on the other layer is drawn
 * like the solid one.</p>
 */
public class DreamSpearRenderer extends EntityRenderer<DreamSpearEntity, DreamSpearRenderState> {
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

    private final ItemModelResolver itemModelResolver;

    public DreamSpearRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
    }

    @Override
    public DreamSpearRenderState createRenderState() {
        return new DreamSpearRenderState();
    }

    @Override
    public void extractRenderState(DreamSpearEntity spear, DreamSpearRenderState state, float partialTicks) {
        super.extractRenderState(spear, state, partialTicks);
        state.ghost = isGhost(spear);
        state.yRot = Mth.lerp(partialTicks, spear.yRotO, spear.getYRot());
        state.xRot = Mth.lerp(partialTicks, spear.xRotO, spear.getXRot());
        state.anchored = spear.isAnchored();
        state.poseOffset = hostOffset(spear, partialTicks);
        this.itemModelResolver.updateForNonLiving(state.item, spear.getItem(),
                ItemDisplayContext.NONE, spear);
    }

    @Override
    public void submit(DreamSpearRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
            CameraRenderState camera) {
        poseStack.pushPose();
        // An anchored spear is drawn on its host, not on the coordinates the entity happens to have:
        // those arrive one round trip late and make the spear trail the body.
        if (state.poseOffset != null) {
            poseStack.translate(state.poseOffset.x, state.poseOffset.y, state.poseOffset.z);
        }
        // The trident's frame: the model pole runs along the flight direction. Reusing it keeps the
        // spear pointing where it actually travels instead of being billboarded at the camera.
        poseStack.mulPose(Axis.YP.rotationDegrees(state.yRot - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(state.xRot + 90.0F));
        // The sprite is drawn diagonally across its canvas; turn it onto the pole axis.
        poseStack.mulPose(Axis.ZP.rotationDegrees(SPRITE_TILT));
        poseStack.scale(SPEAR_SCALE, SPEAR_SCALE, SPEAR_SCALE);
        state.item.submit(poseStack, submitNodeCollector, state.lightCoords,
                OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
        super.submit(state, poseStack, submitNodeCollector, camera);
    }

    public Identifier getTextureLocation(DreamSpearRenderState state) {
        return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS;
    }

    /**
     * Offsets the pose onto the creature the spear is stuck in.
     *
     * <p>The render dispatcher already translated the pose to the entity's own interpolated position,
     * so only the difference to the host's position is applied. Both positions use the same
     * {@code lerp(partialTick, old, current)} the renderer itself uses, which keeps the spear
     * interpolating in step with the body instead of snapping once per tick.</p>
     */
    private static Vec3 hostOffset(DreamSpearEntity spear, float partialTicks) {
        if (!spear.isAnchored() || spear.hostId() < 0) {
            return null;
        }
        Entity host = spear.level().getEntity(spear.hostId());
        if (host == null) {
            return null;
        }
        Vec3 target = renderPosition(host, partialTicks).add(spear.hostOffset());
        Vec3 self = renderPosition(spear, partialTicks);
        return target.subtract(self);
    }

    /** Interpolated position of an entity for this frame, matching {@code EntityRenderDispatcher}. */
    private static Vec3 renderPosition(Entity entity, float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, entity.xOld, entity.getX()),
                Mth.lerp(partialTick, entity.yOld, entity.getY()),
                Mth.lerp(partialTick, entity.zOld, entity.getZ()));
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
}
