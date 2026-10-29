package cc.sighs.dreamworld.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Dream walk: while active, sleeping moves the player into the dream world instead of skipping the
 * night. Removed when the player wakes up.
 */
public class DreamWalkEffect extends MobEffect {
    public static final int COLOR = 0xB36BFF;

    public DreamWalkEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOR);
    }
}
