package cc.sighs.dreamworld.spi;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Loader-independent sending side of the mod's network channel. */
public interface DreamNetwork {
    void sendToPlayer(ServerPlayer player, DreamPacket packet);

    void sendToAll(MinecraftServer server, DreamPacket packet);

    void sendToNear(ServerLevel level, Vec3 center, double radius, DreamPacket packet);

    /** Client to server. */
    void sendToServer(DreamPacket packet);
}
