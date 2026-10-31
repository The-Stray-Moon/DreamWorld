package cc.sighs.dreamworld.client;

import cc.sighs.dreamworld.DreamWorldNeoForge121;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/**
 * Client-side dream visibility.
 *
 * <p>A dreamer sees no ordinary creature at all (players are kept so several dreamers can meet),
 * the dream blub being the only wildlife of the dream layer. Which blub reaches which client is
 * decided on the server, so this class only hides the ordinary mobs and drops the blubs that are
 * still loaded when the dream ends.</p>
 */
@EventBusSubscriber(modid = DreamWorldNeoForge121.MOD_ID, value = Dist.CLIENT)
public final class DreamEntityVisibility {
    /** Dream state of the previous client tick, used to detect the end of a dream. */
    private static boolean wasDreaming;

    private DreamEntityVisibility() {
    }

    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        if (!DreamClientState.isDreaming()) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) {
            return;
        }
        // Dream-side creatures are the only living things a dreamer may see; everything else of the
        // real world is hidden. The server only sends dream creatures to dreamers anyway, this keeps
        // the shared real entities out of the view.
        if (!DreamClientState.dreamEntityIds().contains(entity.getId())) {
            event.setCanceled(true);
        }
    }
}
