package cc.sighs.dreamworld.client;

import cc.sighs.dreamworld.net.DWPayloads;
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
        });
    }

    public static void handleGraffitiList(DWPayloads.GraffitiListPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> DreamClientState.setGraffiti(payload.positions()));
    }

    public static void handleDreamPatch(DWPayloads.DreamPatchPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> DreamClientPatches.accept(payload.chunkPos(), payload.entries()));
    }
}
