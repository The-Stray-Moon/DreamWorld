package cc.sighs.dreamworld.platform;

import cc.sighs.dreamworld.net.DWPayloads;
import cc.sighs.dreamworld.spi.DreamNetwork;
import cc.sighs.dreamworld.spi.DreamPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

/** Forge 1.19.2 sending side of the mod's network channel. */
public final class ForgeNetwork implements DreamNetwork {
    @Override
    public void sendToPlayer(ServerPlayer player, DreamPacket packet) {
        DWPayloads.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    @Override
    public void sendToAll(MinecraftServer server, DreamPacket packet) {
        DWPayloads.CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }

    @Override
    public void sendToNear(ServerLevel level, Vec3 center, double radius, DreamPacket packet) {
        DWPayloads.CHANNEL.send(PacketDistributor.NEAR.with(() ->
                new PacketDistributor.TargetPoint(center.x, center.y, center.z, radius,
                        level.dimension())), packet);
    }

    @Override
    public void sendToServer(DreamPacket packet) {
        DWPayloads.CHANNEL.sendToServer(packet);
    }
}
