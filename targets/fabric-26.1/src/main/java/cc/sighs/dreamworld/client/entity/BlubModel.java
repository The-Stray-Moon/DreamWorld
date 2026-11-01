package cc.sighs.dreamworld.client.entity;

import cc.sighs.dreamworld.DreamWorld;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;

/**
 * Model of the Blub, a blob with four stubby limbs and two long ears.
 *
 * <p>The parts are the ones the Blockbench rig exported: {@code Body} with a tail cube, four limbs
 * and two ears. All of them are animated, so the model also owns the baked clips: idle and walk run
 * while the Blub is on its feet, and resting plays the {@code LIE_DOWN} transition once, loops
 * {@code LYING} and plays {@code GET_UP} on the way back up.</p>
 *
 * <p>26.1 removed {@code HierarchicalModel}: the model is stateless and reads everything from a
 * {@link BlubRenderState}, with the clips pre-baked into {@link KeyframeAnimation}s.</p>
 */
public class BlubModel extends EntityModel<BlubRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(DreamWorld.MOD_ID, "blub"), "main");

    private static final String BODY = "Body";
    private static final String LEFT_HAND = "Left_Hand";
    private static final String RIGHT_HAND = "Right_Hand";
    private static final String RIGHT_LEG = "Right_Leg";
    private static final String LEFT_LEG = "Left_Leg";
    private static final String LEFT_EAR = "Left_Ear";
    private static final String RIGHT_EAR = "Right_Ear";

    /** Length of the one-shot transitions, in milliseconds. */
    private static final long LIE_DOWN_MILLIS = 958L;
    private static final long GET_UP_MILLIS = 1042L;
    /** Limb speed above which the walk cycle replaces the idle one. */
    private static final float WALK_THRESHOLD = 0.05F;

    private final KeyframeAnimation idle;
    private final KeyframeAnimation lieDown;
    private final KeyframeAnimation lying;
    private final KeyframeAnimation getUp;
    private final KeyframeAnimation walk;

    public BlubModel(ModelPart root) {
        super(root);
        this.idle = BlubAnimations.IDLE.bake(root);
        this.lieDown = BlubAnimations.LIE_DOWN.bake(root);
        this.lying = BlubAnimations.LYING.bake(root);
        this.getUp = BlubAnimations.GET_UP.bake(root);
        this.walk = BlubAnimations.WALK.bake(root);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild(BODY, CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-5.0F, -7.0F, -1.0F, 10.0F, 7.0F, 10.0F,
                                new CubeDeformation(0.0F))
                        .texOffs(0, 27).addBox(-1.0F, -5.0F, 9.0F, 2.0F, 2.0F, 1.0F,
                                new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 22.0F, -4.0F));

        root.addOrReplaceChild(LEFT_HAND, CubeListBuilder.create()
                        .texOffs(16, 17).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(3.7F, 22.0F, -3.8F));
        root.addOrReplaceChild(RIGHT_HAND, CubeListBuilder.create()
                        .texOffs(16, 21).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-3.8F, 22.0F, -3.8F));
        root.addOrReplaceChild(RIGHT_LEG, CubeListBuilder.create()
                        .texOffs(0, 23).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-3.8F, 22.0F, 3.8F));
        root.addOrReplaceChild(LEFT_LEG, CubeListBuilder.create()
                        .texOffs(8, 23).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(3.8F, 22.0F, 3.8F));

        root.addOrReplaceChild(LEFT_EAR, CubeListBuilder.create()
                        .texOffs(0, 17).addBox(-1.5F, -4.4F, -1.0F, 3.0F, 5.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(2.5F, 15.4F, -2.6F, -0.0004F, 0.0018F, 0.0872F));
        root.addOrReplaceChild(RIGHT_EAR, CubeListBuilder.create()
                        .texOffs(8, 17).addBox(-2.0F, -4.0F, 0.0F, 3.0F, 5.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-2.0F, 15.0F, -3.6F, 0.0054F, -0.0095F, -0.5235F));

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(BlubRenderState state) {
        super.setupAnim(state);
        if (state.clips == null) {
            return;
        }
        if (state.resting != state.clips.wasResting) {
            state.clips.wasResting = state.resting;
            if (state.resting) {
                state.clips.lieDown.start(state.tickCount);
                state.clips.lying.stop();
                state.clips.getUp.stop();
            } else {
                state.clips.getUp.start(state.tickCount);
                state.clips.lieDown.stop();
                state.clips.lying.stop();
            }
        }
        if (state.resting) {
            animateResting(state);
            return;
        }
        if (state.clips.getUp.isStarted()) {
            this.getUp.apply(state.clips.getUp, state.ageInTicks);
            if (state.clips.getUp.getTimeInMillis(state.ageInTicks) >= GET_UP_MILLIS) {
                state.clips.getUp.stop();
            }
            return;
        }
        if (state.walkAnimationSpeed > WALK_THRESHOLD) {
            state.clips.idle.stop();
            this.walk.applyWalk(state.walkAnimationPos, state.walkAnimationSpeed, 2.0F, 2.5F);
        } else {
            state.clips.idle.startIfStopped(state.tickCount);
            this.idle.apply(state.clips.idle, state.ageInTicks);
        }
    }

    /**
     * Plays the lying sequence: the one-shot transition first, then the loop.
     *
     * <p>The two clips animate the same parts, so only one of them may run per frame; the hand-off
     * happens once the transition has run out.</p>
     */
    private void animateResting(BlubRenderState state) {
        if (state.clips.lieDown.isStarted()) {
            this.lieDown.apply(state.clips.lieDown, state.ageInTicks);
            if (state.clips.lieDown.getTimeInMillis(state.ageInTicks) >= LIE_DOWN_MILLIS) {
                state.clips.lieDown.stop();
                state.clips.lying.start(state.tickCount);
            }
            return;
        }
        state.clips.lying.startIfStopped(state.tickCount);
        this.lying.apply(state.clips.lying, state.ageInTicks);
    }
}
