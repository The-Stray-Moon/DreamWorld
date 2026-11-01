package cc.sighs.dreamworld.platform;

import cc.sighs.dreamworld.DreamWorld;
import cc.sighs.dreamworld.boat.DreamTunnelBoat;
import cc.sighs.dreamworld.boat.TunnelGeometry;
import cc.sighs.dreamworld.client.DWClientPayloadHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * The mod's 1.20.1 network channel.
 *
 * <p>Registration happens during mod construction, before the first server or client packet can be
 * sent. Client-bound handlers are pushed onto the client thread before they touch shared client
 * state; the one server-bound message is handled on the main thread as well, because it mutates a
 * boat.</p>
 */
public final class ForgeChannel {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(DreamWorld.MOD_ID, "main"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();

    private static boolean registered;

    private ForgeChannel() {
    }

    /** Registers every message once. Called from the mod constructor. */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        int id = 0;
        CHANNEL.messageBuilder(ForgeMessages.DreamState.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ForgeMessages.DreamState::encode)
                .decoder(ForgeMessages.DreamState::decode)
                .consumerMainThread((message, context) -> {
                    complete(context);
                    DWClientPayloadHandler.handleDreamState(message.common());
                })
                .add();
        CHANNEL.messageBuilder(ForgeMessages.GraffitiList.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ForgeMessages.GraffitiList::encode)
                .decoder(ForgeMessages.GraffitiList::decode)
                .consumerMainThread((message, context) -> {
                    complete(context);
                    DWClientPayloadHandler.handleGraffitiList(message.common());
                })
                .add();
        CHANNEL.messageBuilder(ForgeMessages.DreamPatch.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ForgeMessages.DreamPatch::encode)
                .decoder(ForgeMessages.DreamPatch::decode)
                .consumerMainThread((message, context) -> {
                    complete(context);
                    DWClientPayloadHandler.handleDreamPatch(message.common());
                })
                .add();
        CHANNEL.messageBuilder(ForgeMessages.DreamEntities.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ForgeMessages.DreamEntities::encode)
                .decoder(ForgeMessages.DreamEntities::decode)
                .consumerMainThread((message, context) -> {
                    complete(context);
                    DWClientPayloadHandler.handleDreamEntities(message.common());
                })
                .add();
        CHANNEL.messageBuilder(ForgeMessages.PetalShape.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ForgeMessages.PetalShape::encode)
                .decoder(ForgeMessages.PetalShape::decode)
                .consumerMainThread((message, context) -> {
                    complete(context);
                    DWClientPayloadHandler.handlePetalShape(message.common());
                })
                .add();
        CHANNEL.messageBuilder(ForgeMessages.BlastParticles.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ForgeMessages.BlastParticles::encode)
                .decoder(ForgeMessages.BlastParticles::decode)
                .consumerMainThread((message, context) -> {
                    complete(context);
                    DWClientPayloadHandler.handleBlastParticles(message.common());
                })
                .add();
        CHANNEL.messageBuilder(ForgeMessages.AnchorState.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ForgeMessages.AnchorState::encode)
                .decoder(ForgeMessages.AnchorState::decode)
                .consumerMainThread((message, context) -> {
                    complete(context);
                    DWClientPayloadHandler.handleAnchorState(message.common());
                })
                .add();
        CHANNEL.messageBuilder(ForgeMessages.Dreamers.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ForgeMessages.Dreamers::encode)
                .decoder(ForgeMessages.Dreamers::decode)
                .consumerMainThread((message, context) -> {
                    complete(context);
                    DWClientPayloadHandler.handleDreamers(message.common());
                })
                .add();
        CHANNEL.messageBuilder(ForgeMessages.TunnelAttach.class, id, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ForgeMessages.TunnelAttach::encode)
                .decoder(ForgeMessages.TunnelAttach::decode)
                .consumerMainThread(ForgeChannel::handleTunnelAttach)
                .add();
    }

    private static void complete(java.util.function.Supplier<NetworkEvent.Context> context) {
        context.get().setPacketHandled(true);
    }

    private static void handleTunnelAttach(ForgeMessages.TunnelAttach message,
            java.util.function.Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context current = context.get();
        current.setPacketHandled(true);
        ServerPlayer player = current.getSender();
        if (player == null) {
            return;
        }
        if (player.getVehicle() instanceof Boat boat && boat instanceof DreamTunnelBoat tunnelBoat) {
            tunnelBoat.attachDreamTunnel(
                    new TunnelGeometry.Pair(message.from(), message.to()));
        }
    }
}
