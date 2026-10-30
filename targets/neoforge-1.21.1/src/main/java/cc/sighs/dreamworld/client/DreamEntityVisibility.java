package cc.sighs.dreamworld.client;

import cc.sighs.dreamworld.DreamWorldNeoForge121;
import cc.sighs.dreamworld.entity.DreamRabbitEntity;
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
 * the dream rabbit being the only wildlife of the dream layer. Which rabbit reaches which client is
 * decided on the server, so this class only hides the ordinary mobs and drops the rabbits that are
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
        if (entity instanceof Player || entity instanceof DreamRabbitEntity) {
            return;
        }
        event.setCanceled(true);
    }

    /**
     * A waking client may still hold the rabbits of its own dream: the server removes them, but the
     * removal packet can arrive later, so the local copies are dropped as soon as the state flips.
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        boolean dreaming = DreamClientState.isDreaming();
        if (wasDreaming && !dreaming) {
            discardRabbits(Minecraft.getInstance().level);
        }
        wasDreaming = dreaming;
    }

    private static void discardRabbits(ClientLevel level) {
        if (level == null) {
            return;
        }
        // The ids are collected first, removing entities while iterating the level would be unsafe.
        List<Integer> ids = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof DreamRabbitEntity) {
                ids.add(entity.getId());
            }
        }
        for (int id : ids) {
            level.removeEntity(id, Entity.RemovalReason.DISCARDED);
        }
    }
}
