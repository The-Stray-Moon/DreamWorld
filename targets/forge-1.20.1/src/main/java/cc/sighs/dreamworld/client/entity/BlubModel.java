package cc.sighs.dreamworld.client.entity;

import cc.sighs.dreamworld.DreamWorld;
import cc.sighs.dreamworld.entity.BlubEntity;
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
import net.minecraft.world.entity.AnimationState;

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
            new ResourceLocation(DreamWorld.MOD_ID, "blub"), "main");

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

    private final ModelPart root;

    /**
     * Animation state per Blub.
     *
     * <p>The model instance is shared by every Blub in the level, so keeping the clips on the model
     * would mix them: one resting and one walking Blub rendered in the same frame would restart the
     * lie-down and get-up clips on every switch, and a body that is drawn lying but keeps bobbing
     * through the transition reads as a bug. The state therefore belongs to the entity.</p>
     */
    private final Map<BlubEntity, Clips> clips = new WeakHashMap<>();

    public BlubModel(ModelPart root) {
        this.root = root;
    }

    /** The four clips of one Blub plus the resting value of its previous frame. */
    private static final class Clips {
        private final AnimationState idle = new AnimationState();
        private final AnimationState lieDown = new AnimationState();
        private final AnimationState lying = new AnimationState();
        private final AnimationState getUp = new AnimationState();
        private boolean wasResting;
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
        Clips clips = this.clips.computeIfAbsent(blub, key -> new Clips());
        // The same state the entity itself uses to stay still, so the drawing and the movement can
        // never disagree about whether this Blub is down.
        boolean resting = blub.isDown();
        if (resting != clips.wasResting) {
            clips.wasResting = resting;
            if (resting) {
                clips.lieDown.start(blub.tickCount);
                clips.lying.stop();
                clips.getUp.stop();
            } else {
                clips.getUp.start(blub.tickCount);
                clips.lieDown.stop();
                clips.lying.stop();
            }
        }
        if (resting) {
            animateResting(clips, blub, ageInTicks);
            return;
        }
        if (clips.getUp.isStarted()) {
            this.animate(clips.getUp, BlubAnimations.GET_UP, ageInTicks);
            if (clips.getUp.getAccumulatedTime() >= GET_UP_MILLIS) {
                clips.getUp.stop();
            }
            return;
        }
        if (limbSwingAmount > WALK_THRESHOLD) {
            clips.idle.stop();
            this.animateWalk(BlubAnimations.WALK, limbSwing, limbSwingAmount, 2.0F, 2.5F);
        } else {
            clips.idle.startIfStopped(blub.tickCount);
            this.animate(clips.idle, BlubAnimations.IDLE, ageInTicks);
        }
    }

    /**
     * Plays the lying sequence: the one-shot transition first, then the loop.
     *
     * <p>The two clips animate the same parts, so only one of them may run per frame; the hand-off
     * happens once the transition has run out.</p>
     */
    private void animateResting(Clips clips, BlubEntity blub, float ageInTicks) {
        if (clips.lieDown.isStarted()) {
            this.animate(clips.lieDown, BlubAnimations.LIE_DOWN, ageInTicks);
            if (clips.lieDown.getAccumulatedTime() >= LIE_DOWN_MILLIS) {
                clips.lieDown.stop();
                clips.lying.start(blub.tickCount);
            }
            return;
        }
        clips.lying.startIfStopped(blub.tickCount);
        this.animate(clips.lying, BlubAnimations.LYING, ageInTicks);
    }
}
