package cc.sighs.dreamworld.client.entity;

import cc.sighs.dreamworld.DreamWorldNeoForge121;
import cc.sighs.dreamworld.entity.BlubEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Pose;

/**
 * Model of the Blub, a blob with four stubby limbs and two long ears.
 *
 * <p>The parts are the ones the Blockbench rig exported: {@code Body} with a tail cube, four limbs
 * and two ears. All of them are animated, so the model also owns the animation states: idle and walk
 * run while the Blub is on its feet, and resting plays the {@code LIE_DOWN} transition once, loops
 * {@code LYING} and plays {@code GET_UP} on the way back up.</p>
 */
public class BlubModel extends HierarchicalModel<BlubEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(DreamWorldNeoForge121.MOD_ID, "blub"), "main");

    private static final String BODY = "Body";
    private static final String LEFT_HAND = "Left_Hand";
    private static final String RIGHT_HAND = "Right_Hand";
    private static final String RIGHT_LEG = "Right_Leg";
    private static final String LEFT_LEG = "Left_Leg";
    private static final String LEFT_EAR = "Left_Ear";
    private static final String RIGHT_EAR = "Right_Ear";

    /** Length of the one-shot transitions, in milliseconds. */
    private static final long LIE_DOWN_MILLIS = 958L;
    private static final long GET_UP_MILLIS = 917L;
    /** Limb speed above which the walk cycle replaces the idle one. */
    private static final float WALK_THRESHOLD = 0.05F;

    private final ModelPart root;

    private final AnimationState idleState = new AnimationState();
    private final AnimationState lieDownState = new AnimationState();
    private final AnimationState lyingState = new AnimationState();
    private final AnimationState getUpState = new AnimationState();
    /** Resting value of the previous frame, used to catch the edges of the transition. */
    private boolean wasResting;

    public BlubModel(ModelPart root) {
        this.root = root;
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
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(BlubEntity blub, float limbSwing, float limbSwingAmount,
            float ageInTicks, float netHeadYaw, float headPitch) {
        this.root.getAllParts().forEach(ModelPart::resetPose);
        // An intruded copy is put to sleep through the entity pose instead of the rest timer, so both
        // roads lead to the same lying clips.
        boolean resting = blub.isResting() || blub.getPose() == Pose.SLEEPING;
        if (resting != this.wasResting) {
            this.wasResting = resting;
            if (resting) {
                this.lieDownState.start(blub.tickCount);
                this.lyingState.stop();
                this.getUpState.stop();
            } else {
                this.getUpState.start(blub.tickCount);
                this.lieDownState.stop();
                this.lyingState.stop();
            }
        }
        if (resting) {
            animateResting(blub, ageInTicks);
            return;
        }
        if (this.getUpState.isStarted()) {
            this.animate(this.getUpState, BlubAnimations.GET_UP, ageInTicks);
            if (this.getUpState.getAccumulatedTime() >= GET_UP_MILLIS) {
                this.getUpState.stop();
            }
            return;
        }
        if (limbSwingAmount > WALK_THRESHOLD) {
            this.idleState.stop();
            this.animateWalk(BlubAnimations.WALK, limbSwing, limbSwingAmount, 2.0F, 2.5F);
        } else {
            this.idleState.startIfStopped(blub.tickCount);
            this.animate(this.idleState, BlubAnimations.IDLE, ageInTicks);
        }
    }

    /**
     * Plays the lying sequence: the one-shot transition first, then the loop.
     *
     * <p>The two clips animate the same parts, so only one of them may run per frame; the hand-off
     * happens once the transition has run out.</p>
     */
    private void animateResting(BlubEntity blub, float ageInTicks) {
        if (this.lieDownState.isStarted()) {
            this.animate(this.lieDownState, BlubAnimations.LIE_DOWN, ageInTicks);
            if (this.lieDownState.getAccumulatedTime() >= LIE_DOWN_MILLIS) {
                this.lieDownState.stop();
                this.lyingState.start(blub.tickCount);
            }
            return;
        }
        this.lyingState.startIfStopped(blub.tickCount);
        this.animate(this.lyingState, BlubAnimations.LYING, ageInTicks);
    }
}
