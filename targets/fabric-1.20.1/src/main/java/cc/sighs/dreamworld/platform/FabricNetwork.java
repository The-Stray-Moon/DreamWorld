package cc.sighs.dreamworld.platform;

import cc.sighs.dreamworld.DreamWorld;
import cc.sighs.dreamworld.net.DWPayloads;
import cc.sighs.dreamworld.net.DreamPackets;
import cc.sighs.dreamworld.spi.DreamNetwork;
import cc.sighs.dreamworld.spi.DreamPacket;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Fabric sending side of the mod's network channel on 1.20.1. */
public final class FabricNetwork implements DreamNetwork {
    /** Registers the client-to-server channels; 1.20.1 has no separate payload type registry. */
    public static void registerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(DWPayloads.TunnelAttachPayload.TYPE,
                (payload, player, sender) -> handleTunnelAttach(payload, player));
    }

    @Override
    public void sendToPlayer(ServerPlayer player, DreamPacket packet) {
        ServerPlayNetworking.send(player, payload(packet));
    }

    @Override
    public void sendToAll(MinecraftServer server, DreamPacket packet) {
        FabricPacket payload = payload(packet);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    @Override
    public void sendToNear(ServerLevel level, Vec3 center, double radius, DreamPacket packet) {
        FabricPacket payload = payload(packet);
        double radiusSqr = radius * radius;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(center) <= radiusSqr) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    @Override
    public void sendToServer(DreamPacket packet) {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(payload(packet));
    }

    private static FabricPacket payload(DreamPacket packet) {
        if (packet instanceof DreamPackets.DreamState state) {
            return new DWPayloads.DreamStatePayload(state.dreaming());
        }
        if (packet instanceof DreamPackets.GraffitiList list) {
            return new DWPayloads.GraffitiListPayload(list.positions(), list.maxTunnelsPerGraffiti());
        }
        if (packet instanceof DreamPackets.DreamPatch patch) {
            return new DWPayloads.DreamPatchPayload(patch.chunkPos(),
                    patch.entries().stream().map(DWPayloads.PatchEntry::of).toList());
        }
        if (packet instanceof DreamPackets.DreamEntities entities) {
            return new DWPayloads.DreamEntitiesPayload(entities.ids());
        }
        if (packet instanceof DreamPackets.PetalShape petal) {
            return new DWPayloads.PetalShapePayload(petal.entityType(), petal.x(), petal.y(),
                    petal.z(), petal.yRot(), petal.count());
        }
        if (packet instanceof DreamPackets.BlastParticles blast) {
            return new DWPayloads.BlastParticlesPayload(blast.x(), blast.y(), blast.z(),
                    blast.radius(), blast.count());
        }
        if (packet instanceof DreamPackets.AnchorState anchor) {
            return new DWPayloads.AnchorStatePayload(anchor.realSideBound(),
                    anchor.dreamSideBound());
        }
        if (packet instanceof DreamPackets.Dreamers dreamers) {
            return new DWPayloads.DreamersPayload(List.copyOf(dreamers.dreamers()));
        }
        if (packet instanceof DreamPackets.TunnelAttach attach) {
            return new DWPayloads.TunnelAttachPayload(attach.from(), attach.to());
        }
        throw new IllegalArgumentException("Unknown DreamWorld packet: " + packet.getClass());
    }

    private static void handleTunnelAttach(DWPayloads.TunnelAttachPayload payload,
            ServerPlayer player) {
        net.minecraft.server.MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            if (player.getVehicle() instanceof net.minecraft.world.entity.vehicle.Boat boat
                    && boat instanceof cc.sighs.dreamworld.boat.DreamTunnelBoat tunnelBoat) {
                tunnelBoat.attachDreamTunnel(new cc.sighs.dreamworld.boat.TunnelGeometry.Pair(
                        payload.from(), payload.to()));
            }
        });
    }

    static ResourceLocation id(String path) {
        return new ResourceLocation(DreamWorld.MOD_ID, path);
    }
}
