package cc.sighs.dreamworld.client;

import cc.sighs.dreamworld.DWRegistry;
import cc.sighs.dreamworld.DreamWorldNeoForge121;
import cc.sighs.dreamworld.client.particle.DreamPetalParticle;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/** Client registration of the mod's own particle types. */
@EventBusSubscriber(modid = DreamWorldNeoForge121.MOD_ID, value = Dist.CLIENT)
public final class DreamParticles {
    private DreamParticles() {
    }

    @SubscribeEvent
    public static void registerProviders(RegisterParticleProvidersEvent event) {
        // The sprite set comes from the vanilla particles atlas, so the petal keeps the cherry
        // texture without shipping a copy of it.
        event.registerSpriteSet(DWRegistry.DREAM_PETAL.get(), DreamPetalParticle.Provider::new);
    }
}
