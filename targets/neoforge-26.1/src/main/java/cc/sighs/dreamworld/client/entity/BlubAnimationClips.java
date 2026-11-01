package cc.sighs.dreamworld.client.entity;

import net.minecraft.world.entity.AnimationState;

/**
 * Animation timers of one Blub.
 *
 * <p>26.1 render states are created fresh for every frame, so an {@link AnimationState} stored only
 * in the render state restarts every frame and freezes the clip at its first keyframe. The clips
 * therefore live per entity and the render state points at them.</p>
 */
public final class BlubAnimationClips {
    public final AnimationState idle = new AnimationState();
    public final AnimationState lieDown = new AnimationState();
    public final AnimationState lying = new AnimationState();
    public final AnimationState getUp = new AnimationState();
    public boolean wasResting;
}
