package cc.sighs.dreamworld.client.entity;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Render state of the thrown spear.
 *
 * <p>26.1 renderers only see a state, so the layer flag, the host the spear rides and the item model
 * are all copied here from the entity.</p>
 */
public class DreamSpearRenderState extends EntityRenderState {
    /** Whether the spear sits on the other layer than the local player. */
    public boolean ghost;
    public boolean anchored;
    /** Pose correction from the spear's own position to the creature it is stuck in. */
    public @Nullable Vec3 poseOffset;
    public float yRot;
    public float xRot;
    public final ItemStackRenderState item = new ItemStackRenderState();
}
