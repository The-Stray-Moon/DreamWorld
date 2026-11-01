package cc.sighs.dreamworld.net;

import cc.sighs.dreamworld.DreamWorld;
import cc.sighs.dreamworld.client.DWClientPayloadHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 1.19.2 wire representation of the shared {@link DreamPackets} records.
 *
 * <p>Forge 1.19.2 has no payload registry; messages are registered on a {@link SimpleChannel}
 * and encoded with a {@link FriendlyByteBuf}. Every record is registered with its own id and
 * direction, and the client-bound consumers are installed only on the physical client.</p>
 */
public final class DWPayloads {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(DreamWorld.MOD_ID, "main"), () -> PROTOCOL,
            PROTOCOL::equals, PROTOCOL::equals);

    private DWPayloads() {
    }

    public static void register() {
        CHANNEL.messageBuilder(DreamPackets.DreamState.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((message, buffer) -> buffer.writeBoolean(message.dreaming()))
                .decoder(buffer -> new DreamPackets.DreamState(buffer.readBoolean()))
                .consumerMainThread((message, context) -> DistExecutor.unsafeRunWhenOn(
                        Dist.CLIENT, () -> () -> DWClientPayloadHandler.handleDreamState(message)))
                .add();

        CHANNEL.messageBuilder(DreamPackets.GraffitiList.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((message, buffer) -> {
                    buffer.writeVarInt(message.positions().size());
                    for (BlockPos pos : message.positions()) {
                        buffer.writeBlockPos(pos);
                    }
                    buffer.writeVarInt(message.maxTunnelsPerGraffiti());
                })
                .decoder(buffer -> {
                    int size = buffer.readVarInt();
                    List<BlockPos> positions = new ArrayList<>(size);
                    for (int index = 0; index < size; index++) {
                        positions.add(buffer.readBlockPos());
                    }
                    return new DreamPackets.GraffitiList(positions, buffer.readVarInt());
                })
                .consumerMainThread((message, context) -> DistExecutor.unsafeRunWhenOn(
                        Dist.CLIENT, () -> () -> DWClientPayloadHandler.handleGraffitiList(message)))
                .add();

        CHANNEL.messageBuilder(DreamPackets.DreamPatch.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((message, buffer) -> {
                    buffer.writeLong(message.chunkPos());
                    buffer.writeVarInt(message.entries().size());
                    for (DreamPackets.PatchEntry entry : message.entries()) {
                        buffer.writeLong(entry.pos());
                        buffer.writeNbt(entry.state());
                        buffer.writeNbt(entry.realState());
                        buffer.writeBoolean(entry.intruded());
                    }
                })
                .decoder(buffer -> {
                    long chunkPos = buffer.readLong();
                    int size = buffer.readVarInt();
                    List<DreamPackets.PatchEntry> entries = new ArrayList<>(size);
                    for (int index = 0; index < size; index++) {
                        long pos = buffer.readLong();
                        CompoundTag state = buffer.readNbt();
                        CompoundTag realState = buffer.readNbt();
                        entries.add(new DreamPackets.PatchEntry(pos, state, realState,
                                buffer.readBoolean()));
                    }
                    return new DreamPackets.DreamPatch(chunkPos, entries);
                })
                .consumerMainThread((message, context) -> DistExecutor.unsafeRunWhenOn(
                        Dist.CLIENT, () -> () -> DWClientPayloadHandler.handleDreamPatch(message)))
                .add();

        CHANNEL.messageBuilder(DreamPackets.DreamEntities.class, 3, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((message, buffer) -> {
                    buffer.writeVarInt(message.ids().size());
                    for (int id : message.ids()) {
                        buffer.writeVarInt(id);
                    }
                })
                .decoder(buffer -> {
                    int size = buffer.readVarInt();
                    List<Integer> ids = new ArrayList<>(size);
                    for (int index = 0; index < size; index++) {
                        ids.add(buffer.readVarInt());
                    }
                    return new DreamPackets.DreamEntities(ids);
                })
                .consumerMainThread((message, context) -> DistExecutor.unsafeRunWhenOn(
                        Dist.CLIENT, () -> () -> DWClientPayloadHandler.handleDreamEntities(message)))
                .add();

        CHANNEL.messageBuilder(DreamPackets.PetalShape.class, 4, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((message, buffer) -> {
                    buffer.writeResourceLocation(message.entityType());
                    buffer.writeDouble(message.x());
                    buffer.writeDouble(message.y());
                    buffer.writeDouble(message.z());
                    buffer.writeFloat(message.yRot());
                    buffer.writeVarInt(message.count());
                })
                .decoder(buffer -> new DreamPackets.PetalShape(buffer.readResourceLocation(),
                        buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                        buffer.readFloat(), buffer.readVarInt()))
                .consumerMainThread((message, context) -> DistExecutor.unsafeRunWhenOn(
                        Dist.CLIENT, () -> () -> DWClientPayloadHandler.handlePetalShape(message)))
                .add();

        CHANNEL.messageBuilder(DreamPackets.BlastParticles.class, 5, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((message, buffer) -> {
                    buffer.writeDouble(message.x());
                    buffer.writeDouble(message.y());
                    buffer.writeDouble(message.z());
                    buffer.writeFloat(message.radius());
                    buffer.writeVarInt(message.count());
                })
                .decoder(buffer -> new DreamPackets.BlastParticles(buffer.readDouble(),
                        buffer.readDouble(), buffer.readDouble(), buffer.readFloat(),
                        buffer.readVarInt()))
                .consumerMainThread((message, context) -> DistExecutor.unsafeRunWhenOn(
                        Dist.CLIENT, () -> () -> DWClientPayloadHandler.handleBlastParticles(message)))
                .add();

        CHANNEL.messageBuilder(DreamPackets.AnchorState.class, 6, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((message, buffer) -> buffer.writeBoolean(message.bound()))
                .decoder(buffer -> new DreamPackets.AnchorState(buffer.readBoolean()))
                .consumerMainThread((message, context) -> DistExecutor.unsafeRunWhenOn(
                        Dist.CLIENT, () -> () -> DWClientPayloadHandler.handleAnchorState(message)))
                .add();

        CHANNEL.messageBuilder(DreamPackets.Dreamers.class, 7, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((message, buffer) -> {
                    buffer.writeVarInt(message.dreamers().size());
                    for (UUID dreamer : message.dreamers()) {
                        buffer.writeUUID(dreamer);
                    }
                })
                .decoder(buffer -> {
                    int size = buffer.readVarInt();
                    List<UUID> dreamers = new ArrayList<>(size);
                    for (int index = 0; index < size; index++) {
                        dreamers.add(buffer.readUUID());
                    }
                    return new DreamPackets.Dreamers(dreamers);
                })
                .consumerMainThread((message, context) -> DistExecutor.unsafeRunWhenOn(
                        Dist.CLIENT, () -> () -> DWClientPayloadHandler.handleDreamers(message)))
                .add();

        CHANNEL.messageBuilder(DreamPackets.TunnelAttach.class, 8, NetworkDirection.PLAY_TO_SERVER)
                .encoder((message, buffer) -> {
                    buffer.writeBlockPos(message.from());
                    buffer.writeBlockPos(message.to());
                })
                .decoder(buffer -> new DreamPackets.TunnelAttach(buffer.readBlockPos(),
                        buffer.readBlockPos()))
                .consumerMainThread((message, context) -> {
                    net.minecraft.server.level.ServerPlayer player = context.get().getSender();
                    if (player == null) {
                        return;
                    }
                    if (player.getVehicle() instanceof net.minecraft.world.entity.vehicle.Boat boat
                            && boat instanceof cc.sighs.dreamworld.boat.DreamTunnelBoat tunnelBoat) {
                        tunnelBoat.attachDreamTunnel(new cc.sighs.dreamworld.boat.TunnelGeometry.Pair(
                                message.from(), message.to()));
                    }
                })
                .add();
    }
}
