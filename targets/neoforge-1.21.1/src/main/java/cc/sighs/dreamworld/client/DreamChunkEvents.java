package cc.sighs.dreamworld.client;

import cc.sighs.dreamworld.DreamWorldNeoForge121;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
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
    private DreamChunkEvents() {
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!DreamClientState.isDreaming() || !event.getLevel().isClientSide()) {
            return;
        }
        DreamClientPatches.applyChunk(event.getChunk().getPos().toLong());
    }
}
