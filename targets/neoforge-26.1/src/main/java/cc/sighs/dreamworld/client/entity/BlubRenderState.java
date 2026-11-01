package cc.sighs.dreamworld.client.entity;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Render state of the Blub: the resting flag and the persistent per-entity animation timers.
 */
public class BlubRenderState extends LivingEntityRenderState {
    public boolean resting;
    public int tickCount;
    public BlubAnimationClips clips;
}
