package cc.sighs.dreamworld.client;

import cc.sighs.dreamworld.DreamWorldNeoForge121;
import cc.sighs.dreamworld.entity.DreamClayEntity;
import cc.sighs.dreamworld.entity.BlubEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * Applies the dream patch set the moment a chunk is loaded on the client.
 *
 * <p>This is the hook that makes the dream layer a real terrain swap instead of a post-load edit:
 * patches that arrived before their chunk are cached by {@link DreamClientPatches}, and they are
 * written into the freshly loaded chunk before the player can walk on it. Collision, lighting and
 * meshes then all come from the dream blocks, so no movement flags have to be touched.</p>
 *
 * <p>The same tick hook keeps the dreamed population out of a client that is not dreaming: every
 * dream blub is dropped from the client level, so an awake player never has one in their world even
 * for a single frame.</p>
 */
@EventBusSubscriber(modid = DreamWorldNeoForge121.MOD_ID, value = Dist.CLIENT)
public final class DreamChunkEvents {
    /** Ticks between two sweeps that drop blubs from a non-dreaming level. */
    private static final int BLUB_SWEEP_INTERVAL = 20;

    private static boolean lastDreaming;
    private static int sweepTicks;

    private DreamChunkEvents() {
    }

    /**
     * Forgets the previous session's dream state when the client leaves a world.
     *
     * <p>Everything the overlay knows is pushed by the server, but the flags and the patch cache are
     * static and outlive a disconnect. A client that quits while dreaming and joins another world
     * would otherwise paint the old dream layer over the new chunks before the server said anything -
     * including turning the ground under the player into air.</p>
     */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        resetSession();
    }

    /** Joining a world starts from a clean slate too, in case the previous session never said goodbye. */
    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        resetSession();
    }

    private static void resetSession() {
        DreamClientState.reset();
        DreamClientPatches.reset();
        lastDreaming = false;
        sweepTicks = 0;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        boolean dreaming = DreamClientState.isDreaming();
        if (dreaming != lastDreaming) {
            lastDreaming = dreaming;
            if (level != null) {
                // Drop the blobs that belong to the layer the player just left.
                List<Entity> stale = new ArrayList<>();
                for (Entity entity : level.entitiesForRendering()) {
                    if (entity instanceof DreamClayEntity clay && clay.isDreamOrigin() != dreaming) {
                        stale.add(entity);
                    }
                }
                stale.forEach(Entity::discard);
                if (!dreaming) {
                    discardBlubs(level);
                }
                minecraft.levelRenderer.allChanged();
            }
        }
        if (!dreaming && level != null && ++sweepTicks % BLUB_SWEEP_INTERVAL == 0) {
            discardBlubs(level);
        }
    }

    /**
     * Removes every dream blub from a client level that is not showing the dream layer.
     *
     * <p>The server already filters them out of the tracking set of a non-dreaming player, but a
     * blub can still be in the level for a moment - a stale packet, a reconnect, a dream that ended
     * while the entity was in flight. The client is the last word on what is visible, so it simply
     * drops them.</p>
     */
    private static void discardBlubs(ClientLevel level) {
        List<Entity> blubs = null;
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof BlubEntity) {
                if (blubs == null) {
                    blubs = new ArrayList<>();
                }
                blubs.add(entity);
            }
        }
        if (blubs != null) {
            blubs.forEach(Entity::discard);
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!DreamClientState.isDreaming() || !event.getLevel().isClientSide()) {
            return;
        }
        DreamClientPatches.applyChunk(event.getChunk().getPos().toLong());
    }
}
