package cc.sighs.dreamworld.client.entity;

import cc.sighs.dreamworld.DWRegistry;
import cc.sighs.dreamworld.DreamWorldNeoForge121;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Client-only renderers for the dream entities.
 *
 * <p>Dream clay is drawn with the vanilla thrown item renderer, the spear has its own renderer so an
 * anchor on the other layer can be shown as a ghost, and the dream rabbit reuses the vanilla rabbit
 * renderer and textures. The rabbit attributes are registered by
 * {@link cc.sighs.dreamworld.entity.DreamRabbitEntity} because that event fires on both sides.</p>
 */
@EventBusSubscriber(modid = DreamWorldNeoForge121.MOD_ID, value = Dist.CLIENT)
public final class DreamEntityRenderers {
    private DreamEntityRenderers() {
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DWRegistry.DREAM_CLAY_ENTITY.get(), ThrownItemRenderer::new);
        event.registerEntityRenderer(DWRegistry.DREAM_SPEAR_ENTITY.get(), DreamSpearRenderer::new);
        event.registerEntityRenderer(DWRegistry.DREAM_RABBIT.get(), DreamRabbitRenderer::new);
    }
}
