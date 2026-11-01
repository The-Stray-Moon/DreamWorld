package cc.sighs.dreamworld.platform;

import cc.sighs.dreamworld.net.DWPayloads;
import cc.sighs.dreamworld.net.DreamPackets;
import cc.sighs.dreamworld.spi.DreamNetwork;
import cc.sighs.dreamworld.spi.DreamPacket;
import java.util.List;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** NeoForge sending side of the mod's network channel. */
public final class NeoForgeNetwork implements DreamNetwork {
    @Override
    public void sendToPlayer(ServerPlayer player, DreamPacket packet) {
        PacketDistributor.sendToPlayer(player, payload(packet));
    }

    @Override
    public void sendToAll(MinecraftServer server, DreamPacket packet) {
        CustomPacketPayload payload = payload(packet);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    @Override
    public void sendToNear(ServerLevel level, Vec3 center, double radius, DreamPacket packet) {
        PacketDistributor.sendToPlayersNear(level, null, center.x, center.y, center.z, radius,
                payload(packet));
    }

    @Override
    public void sendToServer(DreamPacket packet) {
        PacketDistributor.sendToServer(payload(packet));
    }

    private static CustomPacketPayload payload(DreamPacket packet) {
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
            return new DWPayloads.AnchorStatePayload(anchor.bound());
        }
        if (packet instanceof DreamPackets.Dreamers dreamers) {
            return new DWPayloads.DreamersPayload(List.copyOf(dreamers.dreamers()));
        }
        if (packet instanceof DreamPackets.TunnelAttach attach) {
            return new DWPayloads.TunnelAttachPayload(attach.from(), attach.to());
        }
        throw new IllegalArgumentException("Unknown DreamWorld packet: " + packet.getClass());
    }
}
