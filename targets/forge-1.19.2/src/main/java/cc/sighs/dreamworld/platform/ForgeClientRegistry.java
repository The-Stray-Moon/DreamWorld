package cc.sighs.dreamworld.platform;

import cc.sighs.dreamworld.DWObjects;
import cc.sighs.dreamworld.DreamWorld;
import cc.sighs.dreamworld.client.DreamBlink;
import cc.sighs.dreamworld.client.entity.BlubModel;
import cc.sighs.dreamworld.client.entity.BlubRenderer;
import cc.sighs.dreamworld.client.entity.DreamSpearRenderer;
import cc.sighs.dreamworld.client.particle.DreamPetalParticle;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-side registrations that belong to the mod event bus. */
@Mod.EventBusSubscriber(modid = DreamWorld.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class ForgeClientRegistry {
    private ForgeClientRegistry() {
    }

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.register(DWObjects.DREAM_PETAL.get(), DreamPetalParticle.Provider::new);
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DWObjects.DREAM_CLAY_ENTITY.get(), ThrownItemRenderer::new);
        event.registerEntityRenderer(DWObjects.DREAM_SPEAR_ENTITY.get(), DreamSpearRenderer::new);
        event.registerEntityRenderer(DWObjects.BLUB.get(), BlubRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(BlubModel.LAYER, BlubModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(DreamBlink.reloadListener());
    }
}
