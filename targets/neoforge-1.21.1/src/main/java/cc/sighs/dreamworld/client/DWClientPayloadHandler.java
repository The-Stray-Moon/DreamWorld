package cc.sighs.dreamworld.client;

import cc.sighs.dreamworld.net.DWPayloads;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Handlers for the client-bound payloads. Only loaded on the physical client.
 */
public final class DWClientPayloadHandler {
    private DWClientPayloadHandler() {
    }

    public static void handleDreamState(DWPayloads.DreamStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.dreaming()) {
                DreamClientState.setDreaming(true);
                DreamClientPatches.applyAll();
            } else {
                DreamClientState.setDreaming(false);
                DreamClientPatches.restoreAll();
            }
            // The dream tint is baked into the block faces, so the chunk meshes must be rebuilt on
            // every transition: pink going in, original colours coming out.
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null) {
                minecraft.levelRenderer.allChanged();
            }
        });
    }

    public static void handleGraffitiList(DWPayloads.GraffitiListPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> DreamClientState.setGraffiti(payload.positions()));
    }

    public static void handleDreamEntities(DWPayloads.DreamEntitiesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> DreamClientState.setDreamEntityIds(payload.ids()));
    }

    public static void handleDreamPatch(DWPayloads.DreamPatchPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> DreamClientPatches.accept(payload.chunkPos(), payload.entries()));
    }
}
