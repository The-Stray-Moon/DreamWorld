package cc.sighs.dreamworld.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads the vanilla jump key state of a living entity.
 *
 * <p>Both sides keep the field up to date: the client copies {@code Input#jumping} into it every
 * tick and the server fills it from {@code ServerboundPlayerInputPacket}. The boat needs it to give
 * a dreaming rider a jump key, because boats have no jump channel of their own.</p>
 */
@Mixin(LivingEntity.class)
public interface DWLivingEntityAccessor {
    @Accessor("jumping")
    boolean dreamworld$isJumping();
}
