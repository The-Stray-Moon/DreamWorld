package cc.sighs.dreamworld.client.entity;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.AnimationState;

/**
 * Render state of the Blub: the four clips and the resting flag.
 *
 * <p>26.1 models are stateless and animate from a render state, so the per-entity clips that used to
 * live in the model's weak map now belong to this state, copied from the entity every frame.</p>
 */
public class BlubRenderState extends LivingEntityRenderState {
    public boolean resting;
    public int tickCount;
    public boolean wasResting;
    public final AnimationState idle = new AnimationState();
    public final AnimationState lieDown = new AnimationState();
    public final AnimationState lying = new AnimationState();
    public final AnimationState getUp = new AnimationState();
}
