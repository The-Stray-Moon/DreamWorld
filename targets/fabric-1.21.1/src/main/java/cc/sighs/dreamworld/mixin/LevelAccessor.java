package cc.sighs.dreamworld.mixin;

import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Vanilla keeps the synced weather values protected; the platform adapter reads them raw. */
@Mixin(Level.class)
public interface LevelAccessor {
    @Accessor("rainLevel")
    float dreamworld$rainLevel();

    @Accessor("thunderLevel")
    float dreamworld$thunderLevel();
}
