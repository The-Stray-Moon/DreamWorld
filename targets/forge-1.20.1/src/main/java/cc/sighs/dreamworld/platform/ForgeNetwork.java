package cc.sighs.dreamworld.platform;

import cc.sighs.dreamworld.net.DreamPackets;
import cc.sighs.dreamworld.spi.DreamNetwork;
import cc.sighs.dreamworld.spi.DreamPacket;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

/** Forge 1.20.1 sending side of the mod's network channel. */
public final class ForgeNetwork implements DreamNetwork {
    @Override
    public void sendToPlayer(ServerPlayer player, DreamPacket packet) {
        ForgeChannel.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message(packet));
    }

    @Override
    public void sendToAll(MinecraftServer server, DreamPacket packet) {
        Object message = message(packet);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ForgeChannel.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
        }
    }

    @Override
    public void sendToNear(ServerLevel level, Vec3 center, double radius, DreamPacket packet) {
        PacketDistributor.TargetPoint target = new PacketDistributor.TargetPoint(center.x, center.y,
                center.z, radius, level.dimension());
        ForgeChannel.CHANNEL.send(PacketDistributor.NEAR.with(() -> target), message(packet));
    }

    @Override
    public void sendToServer(DreamPacket packet) {
        ForgeChannel.CHANNEL.sendToServer(message(packet));
    }

    private static Object message(DreamPacket packet) {
        if (packet instanceof DreamPackets.DreamState state) {
            return new ForgeMessages.DreamState(state.dreaming());
        }
        if (packet instanceof DreamPackets.GraffitiList list) {
            return new ForgeMessages.GraffitiList(list.positions(), list.maxTunnelsPerGraffiti());
        }
        if (packet instanceof DreamPackets.DreamPatch patch) {
            return new ForgeMessages.DreamPatch(patch.chunkPos(),
                    patch.entries().stream().map(ForgeMessages.PatchEntry::of).toList());
        }
        if (packet instanceof DreamPackets.DreamEntities entities) {
            return new ForgeMessages.DreamEntities(entities.ids());
        }
        if (packet instanceof DreamPackets.PetalShape petal) {
            return new ForgeMessages.PetalShape(petal.entityType(), petal.x(), petal.y(),
                    petal.z(), petal.yRot(), petal.count());
        }
        if (packet instanceof DreamPackets.BlastParticles blast) {
            return new ForgeMessages.BlastParticles(blast.x(), blast.y(), blast.z(),
                    blast.radius(), blast.count());
        }
        if (packet instanceof DreamPackets.AnchorState anchor) {
            return new ForgeMessages.AnchorState(anchor.realSideBound(), anchor.dreamSideBound());
        }
        if (packet instanceof DreamPackets.Dreamers dreamers) {
            return new ForgeMessages.Dreamers(List.copyOf(dreamers.dreamers()));
        }
        if (packet instanceof DreamPackets.TunnelAttach attach) {
            return new ForgeMessages.TunnelAttach(attach.from(), attach.to());
        }
        throw new IllegalArgumentException("Unknown DreamWorld packet: " + packet.getClass());
    }
}
