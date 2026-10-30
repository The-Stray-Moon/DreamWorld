package cc.sighs.dreamworld.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.EffectParticleModificationEvent;

/**
 * Dream walk: while active, sleeping moves the player into the dream world instead of skipping the
 * night. Removed when the player wakes up.
 *
 * <p>The buff is deliberately silent: it must not draw the colored swirls vanilla effects show
 * around their owner. {@link MobEffect#createParticleOptions} cannot answer that, because it never
 * returns null (the particle list it feeds is synced to the clients and has no room for an absent
 * entry), so the effect is dropped from the synchronized particle list through
 * {@link EffectParticleModificationEvent} instead.</p>
 */
public class DreamWalkEffect extends MobEffect {
    public static final int COLOR = 0xB36BFF;

    static {
        NeoForge.EVENT_BUS.addListener(EffectParticleModificationEvent.class, DreamWalkEffect::hideParticles);
    }

    public DreamWalkEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOR);
    }

    private static void hideParticles(EffectParticleModificationEvent event) {
        if (event.getEffect().getEffect().value() instanceof DreamWalkEffect) {
            event.setVisible(false);
        }
    }
}
