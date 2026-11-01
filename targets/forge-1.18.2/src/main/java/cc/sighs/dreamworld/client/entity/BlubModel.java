package cc.sighs.dreamworld.client.entity;

import cc.sighs.dreamworld.DreamWorld;
import cc.sighs.dreamworld.entity.BlubEntity;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;
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
import net.minecraft.util.Mth;

/**
 * Model of the Blub, a blob with four stubby limbs and two long ears.
 *
 * <p>The parts are the ones the Blockbench rig exported: {@code Body} with a tail cube, four limbs
 * and two ears. 1.18.2 has no animation system, so the clips the 1.21.1 target ships through
 * {@code AnimationDefinition} are recreated procedurally here with the same key poses: an idle
 * breathing loop, a walk cycle and the lying/standing transition.</p>
 */
public class BlubModel extends HierarchicalModel<BlubEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            new ResourceLocation(DreamWorld.MOD_ID, "blub"), "main");

    private static final String BODY = "Body";
    private static final String LEFT_HAND = "Left_Hand";
    private static final String RIGHT_HAND = "Right_Hand";
    private static final String RIGHT_LEG = "Right_Leg";
    private static final String LEFT_LEG = "Left_Leg";
    private static final String LEFT_EAR = "Left_Ear";
    private static final String RIGHT_EAR = "Right_Ear";

    /** Limb speed above which the walk cycle replaces the idle one. */
    private static final float WALK_THRESHOLD = 0.05F;
    /** Fraction of the lie-down transition a frame advances; about a second at 20 fps. */
    private static final float REST_STEP = 0.05F;

    private final ModelPart root;
    private final Map<ModelPart, float[]> rest = new IdentityHashMap<>();
    /** How far each Blub is into lying down, so the transition is per entity, not per model. */
    private final Map<BlubEntity, Float> resting = new WeakHashMap<>();

    public BlubModel(ModelPart root) {
        this.root = root;
        ModelPart body = root.getChild(BODY);
        ModelPart leftHand = root.getChild(LEFT_HAND);
        ModelPart rightHand = root.getChild(RIGHT_HAND);
        ModelPart rightLeg = root.getChild(RIGHT_LEG);
        ModelPart leftLeg = root.getChild(LEFT_LEG);
        ModelPart leftEar = root.getChild(LEFT_EAR);
        ModelPart rightEar = root.getChild(RIGHT_EAR);
        for (ModelPart part : new ModelPart[]{body, leftHand, rightHand, rightLeg, leftLeg,
                leftEar, rightEar}) {
            this.rest.put(part, new float[]{part.x, part.y, part.z, part.xRot, part.yRot, part.zRot});
        }
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
                        .texOffs(16, 17).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 2.0F, 2.0F,
                                new CubeDeformation(0.0F)),
                PartPose.offset(3.7F, 22.0F, -3.8F));
        root.addOrReplaceChild(RIGHT_HAND, CubeListBuilder.create()
                        .texOffs(16, 21).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 2.0F, 2.0F,
                                new CubeDeformation(0.0F)),
                PartPose.offset(-3.8F, 22.0F, -3.8F));
        root.addOrReplaceChild(RIGHT_LEG, CubeListBuilder.create()
                        .texOffs(0, 23).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 2.0F, 2.0F,
                                new CubeDeformation(0.0F)),
                PartPose.offset(-3.8F, 22.0F, 3.8F));
        root.addOrReplaceChild(LEFT_LEG, CubeListBuilder.create()
                        .texOffs(8, 23).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 2.0F, 2.0F,
                                new CubeDeformation(0.0F)),
                PartPose.offset(3.8F, 22.0F, 3.8F));

        root.addOrReplaceChild(LEFT_EAR, CubeListBuilder.create()
                        .texOffs(0, 17).addBox(-1.5F, -4.4F, -1.0F, 3.0F, 5.0F, 1.0F,
                                new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(2.5F, 15.4F, -2.6F, -0.0004F, 0.0018F, 0.0872F));
        root.addOrReplaceChild(RIGHT_EAR, CubeListBuilder.create()
                        .texOffs(8, 17).addBox(-2.0F, -4.0F, 0.0F, 3.0F, 5.0F, 1.0F,
                                new CubeDeformation(0.0F)),
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
        this.rest.forEach((part, pose) -> {
            part.x = pose[0];
            part.y = pose[1];
            part.z = pose[2];
            part.xRot = pose[3];
            part.yRot = pose[4];
            part.zRot = pose[5];
        });

        ModelPart body = this.root.getChild(BODY);
        ModelPart leftHand = this.root.getChild(LEFT_HAND);
        ModelPart rightHand = this.root.getChild(RIGHT_HAND);
        ModelPart rightLeg = this.root.getChild(RIGHT_LEG);
        ModelPart leftLeg = this.root.getChild(LEFT_LEG);
        ModelPart leftEar = this.root.getChild(LEFT_EAR);
        ModelPart rightEar = this.root.getChild(RIGHT_EAR);

        // The same state the entity itself uses to stay still, so the drawing and the movement can
        // never disagree about whether this Blub is down.
        boolean down = blub.isDown();
        float current = this.resting.getOrDefault(blub, down ? 1.0F : 0.0F);
        float target = down ? 1.0F : 0.0F;
        if (current < target) {
            current = Math.min(target, current + REST_STEP);
        } else if (current > target) {
            current = Math.max(target, current - REST_STEP);
        }
        this.resting.put(blub, current);

        if (current > 0.0F) {
            applyResting(body, leftHand, rightHand, rightLeg, leftLeg, leftEar, rightEar, current);
            if (current >= 1.0F) {
                return;
            }
        }

        float breath = 0.5F - 0.5F * Mth.cos(ageInTicks * 0.18F);
        body.xRot -= 7.5F * breath * Mth.DEG_TO_RAD;
        leftHand.xRot -= 2.5F * breath * Mth.DEG_TO_RAD;
        rightHand.xRot -= 2.5F * breath * Mth.DEG_TO_RAD;
        leftEar.xRot += (-15.0F - 22.5F * breath) * Mth.DEG_TO_RAD;
        rightEar.xRot += (-15.0F - 22.5F * breath) * Mth.DEG_TO_RAD;
        rightEar.y += -0.2F * breath;
        rightEar.z += 1.0F * breath;
        leftEar.y += -0.2F - 0.5F * breath;
        leftEar.z += -0.1F + 0.7F * breath;

        if (limbSwingAmount > WALK_THRESHOLD) {
            float swing = Mth.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount;
            float opposite = Mth.cos(limbSwing * 0.6662F + Mth.PI) * 1.4F * limbSwingAmount;
            leftHand.xRot += opposite * 0.6F;
            rightHand.xRot += swing * 0.6F;
            rightLeg.xRot += swing * 0.6F;
            leftLeg.xRot += opposite * 0.6F;
            body.xRot += Mth.cos(limbSwing * 1.3324F) * 0.05F * limbSwingAmount;
            body.y -= 0.2F * Math.abs(Mth.sin(limbSwing * 0.6662F)) * limbSwingAmount;
            leftEar.zRot += 5.0F * Mth.DEG_TO_RAD * Mth.sin(limbSwing);
            rightEar.zRot -= 5.0F * Mth.DEG_TO_RAD * Mth.sin(limbSwing);
        }

        // Fade the waking pose over the same transition so getting up never snaps.
        if (current > 0.0F) {
            float awake = 1.0F - current;
            body.xRot *= awake;
            leftHand.xRot *= awake;
            rightHand.xRot *= awake;
            rightLeg.xRot *= awake;
            leftLeg.xRot *= awake;
        }
    }

    /** Folds the limbs and lowers the body; {@code amount} is the eased 0..1 lying progress. */
    private static void applyResting(ModelPart body, ModelPart leftHand, ModelPart rightHand,
            ModelPart rightLeg, ModelPart leftLeg, ModelPart leftEar, ModelPart rightEar,
            float amount) {
        body.y -= 2.1F * amount;
        leftHand.y -= 2.1F * amount;
        leftHand.x += 0.5F * amount;
        leftHand.z -= 0.5F * amount;
        leftHand.xRot += -57.0F * Mth.DEG_TO_RAD * amount;
        leftHand.yRot += 10.0F * Mth.DEG_TO_RAD * amount;
        leftHand.zRot += -80.0F * Mth.DEG_TO_RAD * amount;

        rightHand.y -= 2.1F * amount;
        rightHand.x -= 0.5F * amount;
        rightHand.z -= 0.5F * amount;
        rightHand.xRot += -57.0F * Mth.DEG_TO_RAD * amount;
        rightHand.yRot += 10.0F * Mth.DEG_TO_RAD * amount;
        rightHand.zRot += 80.0F * Mth.DEG_TO_RAD * amount;

        rightLeg.y -= 2.1F * amount;
        rightLeg.x -= 0.5F * amount;
        rightLeg.z += 0.5F * amount;
        rightLeg.xRot += 57.0F * Mth.DEG_TO_RAD * amount;
        rightLeg.yRot += 10.0F * Mth.DEG_TO_RAD * amount;
        rightLeg.zRot += 80.0F * Mth.DEG_TO_RAD * amount;

        leftLeg.y -= 2.1F * amount;
        leftLeg.x += 0.5F * amount;
        leftLeg.z += 0.5F * amount;
        leftLeg.xRot += 57.0F * Mth.DEG_TO_RAD * amount;
        leftLeg.yRot += 10.0F * Mth.DEG_TO_RAD * amount;
        leftLeg.zRot += -80.0F * Mth.DEG_TO_RAD * amount;

        leftEar.y -= 3.0F * amount;
        rightEar.y -= 3.0F * amount;
        rightEar.xRot += 20.0F * Mth.DEG_TO_RAD * amount;
    }
}
