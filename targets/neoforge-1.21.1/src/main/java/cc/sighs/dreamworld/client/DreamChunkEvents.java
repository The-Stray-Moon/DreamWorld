package cc.sighs.dreamworld.client;

import cc.sighs.dreamworld.DreamWorldNeoForge121;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * Applies the dream patch set the moment a chunk is loaded on the client.
 *
 * <p>This is the hook that makes the dream layer a real terrain swap instead of a post-load edit:
 * patches that arrived before their chunk are cached by {@link DreamClientPatches}, and they are
 * written into the freshly loaded chunk before the player can walk on it. Collision, lighting and
 * meshes then all come from the dream blocks, so no movement flags have to be touched.</p>
 */
@EventBusSubscriber(modid = DreamWorldNeoForge121.MOD_ID, value = Dist.CLIENT)
public final class DreamChunkEvents {
    private static boolean lastDreaming;

    private DreamChunkEvents() {
    }

    /**
     * Safety net for the baked block tint: if the state flips without the payload handler having
     * rebuilt the meshes (login restore, reconnect), rebuild them on the next client tick.
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        boolean dreaming = DreamClientState.isDreaming();
        if (dreaming != lastDreaming) {
            lastDreaming = dreaming;
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null) {
                // Drop the blobs that belong to the layer the player just left.
                java.util.List<net.minecraft.world.entity.Entity> stale = new java.util.ArrayList<>();
                for (net.minecraft.world.entity.Entity entity : minecraft.level.entitiesForRendering()) {
                    if (entity instanceof cc.sighs.dreamworld.entity.DreamClayEntity clay
                            && clay.isDreamOrigin() != dreaming) {
                        stale.add(clay);
                    }
                }
                stale.forEach(net.minecraft.world.entity.Entity::discard);
                minecraft.levelRenderer.allChanged();
            }
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
