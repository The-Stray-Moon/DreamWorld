package cc.sighs.dreamworld.registry;

import net.minecraft.core.particles.SimpleParticleType;

/** Vanilla's constructor is protected; the particle is registered as this trivial subtype. */
public class FabricSimpleParticleType extends SimpleParticleType {
    public FabricSimpleParticleType(boolean alwaysShow) {
        super(alwaysShow);
    }
}
