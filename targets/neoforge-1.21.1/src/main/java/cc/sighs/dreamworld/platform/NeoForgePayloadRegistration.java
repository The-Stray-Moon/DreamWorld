package cc.sighs.dreamworld.platform;

import cc.sighs.dreamworld.DreamWorld;
import cc.sighs.dreamworld.boat.DreamTunnelBoat;
import cc.sighs.dreamworld.boat.TunnelGeometry;
import cc.sighs.dreamworld.client.DWClientPayloadHandler;
import cc.sighs.dreamworld.net.DWPayloads;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.vehicle.Boat;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers the shared 1.21.1 payloads on NeoForge's channel. */
@EventBusSubscriber(modid = DreamWorld.MOD_ID)
public final class NeoForgePayloadRegistration {
    private NeoForgePayloadRegistration() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(DWPayloads.TunnelAttachPayload.TYPE,
                DWPayloads.TunnelAttachPayload.STREAM_CODEC,
                NeoForgePayloadRegistration::handleTunnelAttach);
        registrar.playToClient(DWPayloads.DreamStatePayload.TYPE,
                DWPayloads.DreamStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> DWClientPayloadHandler.handleDreamState(payload.common())));
        registrar.playToClient(DWPayloads.DreamersPayload.TYPE,
                DWPayloads.DreamersPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> DWClientPayloadHandler.handleDreamers(payload.common())));
        registrar.playToClient(DWPayloads.BlastParticlesPayload.TYPE,
                DWPayloads.BlastParticlesPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> DWClientPayloadHandler.handleBlastParticles(payload.common())));
        registrar.playToClient(DWPayloads.PetalShapePayload.TYPE,
                DWPayloads.PetalShapePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> DWClientPayloadHandler.handlePetalShape(payload.common())));
        registrar.playToClient(DWPayloads.AnchorStatePayload.TYPE,
                DWPayloads.AnchorStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> DWClientPayloadHandler.handleAnchorState(payload.common())));
        registrar.playToClient(DWPayloads.GraffitiListPayload.TYPE,
                DWPayloads.GraffitiListPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> DWClientPayloadHandler.handleGraffitiList(payload.common())));
        registrar.playToClient(DWPayloads.DreamEntitiesPayload.TYPE,
                DWPayloads.DreamEntitiesPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> DWClientPayloadHandler.handleDreamEntities(payload.common())));
        registrar.playToClient(DWPayloads.DreamPatchPayload.TYPE,
                DWPayloads.DreamPatchPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> DWClientPayloadHandler.handleDreamPatch(payload.common())));
    }

    public static void handleTunnelAttach(DWPayloads.TunnelAttachPayload payload,
            IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (player.getVehicle() instanceof Boat boat
                    && boat instanceof DreamTunnelBoat tunnelBoat) {
                tunnelBoat.attachDreamTunnel(new TunnelGeometry.Pair(payload.from(), payload.to()));
            }
        });
    }
}
