package cc.sighs.dreamworld.client;

import cc.sighs.dreamworld.DreamWorldNeoForge121;
import cc.sighs.dreamworld.entity.DreamRabbitEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/**
 * Dream-only entity visibility.
 *
 * <p>Dream rabbits only exist for dreaming players, and a dreamer sees no other creature at all
 * (players are kept so several dreamers can meet). This is a pure client rule, so the shared server
 * entity list is never touched.</p>
 */
@EventBusSubscriber(modid = DreamWorldNeoForge121.MOD_ID, value = Dist.CLIENT)
public final class DreamEntityVisibility {
    private DreamEntityVisibility() {
    }

    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity entity = event.getEntity();
        boolean dreaming = DreamClientState.isDreaming();
        if (entity instanceof DreamRabbitEntity) {
            if (!dreaming) {
                event.setCanceled(true);
            }
            return;
        }
        if (dreaming && !(entity instanceof Player)) {
            event.setCanceled(true);
        }
    }
}
