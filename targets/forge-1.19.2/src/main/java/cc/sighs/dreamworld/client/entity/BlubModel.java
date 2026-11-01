package cc.sighs.dreamworld.client.entity;

import cc.sighs.dreamworld.DreamWorld;
import cc.sighs.dreamworld.entity.BlubEntity;
import net.minecraft.client.model.EntityModel;
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
 * <p>1.19.2 has no keyframe animation framework, so the four Blockbench clips (idle, walk,
 * lie down/lie still/get up) are reproduced with plain {@link ModelPart} rotations. The part
 * layout is the exported rig's, unchanged.</p>
 */
public class BlubModel extends EntityModel<BlubEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            new ResourceLocation(DreamWorld.MOD_ID, "blub"), "main");

    private static final String BODY = "Body";
    private static final String LEFT_HAND = "Left_Hand";
    private static final String RIGHT_HAND = "Right_Hand";
    private static final String RIGHT_LEG = "Right_Leg";
    private static final String LEFT_LEG = "Left_Leg";
    private static final String LEFT_EAR = "Left_Ear";
    private static final String RIGHT_EAR = "Right_Ear";

    private static final float WALK_THRESHOLD = 0.05F;

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart leftHand;
    private final ModelPart rightHand;
    private final ModelPart leftLeg;
    private final ModelPart rightLeg;
    private final ModelPart leftEar;
    private final ModelPart rightEar;

    public BlubModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild(BODY);
        this.leftHand = root.getChild(LEFT_HAND);
        this.rightHand = root.getChild(RIGHT_HAND);
        this.leftLeg = root.getChild(LEFT_LEG);
        this.rightLeg = root.getChild(RIGHT_LEG);
        this.leftEar = root.getChild(LEFT_EAR);
        this.rightEar = root.getChild(RIGHT_EAR);
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
    public void setupAnim(BlubEntity blub, float limbSwing, float limbSwingAmount,
            float ageInTicks, float netHeadYaw, float headPitch) {
        this.root.getAllParts().forEach(ModelPart::resetPose);
        if (blub.isDown()) {
            animateResting(ageInTicks);
            return;
        }
        if (limbSwingAmount > WALK_THRESHOLD) {
            animateWalk(limbSwing, limbSwingAmount);
            return;
        }
        animateIdle(ageInTicks);
    }

    private void animateIdle(float ageInTicks) {
        float breath = Mth.sin(ageInTicks * 0.09F) * 0.04F;
        this.body.y += breath;
        this.leftEar.zRot += Mth.sin(ageInTicks * 0.06F) * 0.06F;
        this.rightEar.zRot -= Mth.sin(ageInTicks * 0.06F) * 0.06F;
        this.leftEar.xRot += Mth.cos(ageInTicks * 0.05F) * 0.04F;
    }

    private void animateWalk(float limbSwing, float limbSwingAmount) {
        float swing = Mth.cos(limbSwing * 0.6662F) * 0.7F * limbSwingAmount;
        this.leftLeg.xRot = swing;
        this.rightLeg.xRot = -swing;
        this.leftHand.xRot = -swing;
        this.rightHand.xRot = swing;
        this.body.y -= Math.abs(Mth.sin(limbSwing * 0.6662F)) * 0.6F * limbSwingAmount;
        this.leftEar.xRot += swing * 0.3F;
        this.rightEar.xRot -= swing * 0.3F;
    }

    /**
     * Lying pose: the body is rolled forward onto its stomach and lowered, the limbs splay out
     * and the ears droop. The transition is a short ease so entering and leaving the rest does
     * not pop.
     */
    private void animateResting(float ageInTicks) {
        float settle = Math.min(1.0F, ageInTicks * 0.12F);
        this.body.xRot += 1.35F * settle;
        this.body.y += 3.0F * settle;
        this.root.y += 1.5F * settle;
        this.leftHand.xRot -= 0.9F * settle;
        this.rightHand.xRot -= 0.9F * settle;
        this.leftLeg.xRot += 0.9F * settle;
        this.rightLeg.xRot += 0.9F * settle;
        this.leftEar.xRot += 1.2F * settle;
        this.rightEar.xRot += 1.2F * settle;
        this.leftEar.zRot += 0.2F * settle;
        this.rightEar.zRot -= 0.2F * settle;
    }

    @Override
    public void renderToBuffer(com.mojang.blaze3d.vertex.PoseStack poseStack,
            com.mojang.blaze3d.vertex.VertexConsumer vertexConsumer, int packedLight,
            int packedOverlay, float red, float green, float blue, float alpha) {
        this.root.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue,
                alpha);
    }
}
