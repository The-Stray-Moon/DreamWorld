package cc.sighs.dreamworld.net;

import cc.sighs.dreamworld.DreamWorld;
import cc.sighs.dreamworld.boat.DreamTunnelBoat;
import cc.sighs.dreamworld.boat.TunnelGeometry;
import cc.sighs.dreamworld.client.DWClientPayloadHandler;
import cc.sighs.dreamworld.spi.DreamPacket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Forge 1.18.2 wire representation of the shared {@link DreamPackets}.
 *
 * <p>1.18.2 has no payload API: the messages are plain records carried by a
 * {@link SimpleChannel}. Only the strings of the shared protocol are duplicated here, the
 * records themselves stay in the shared tree.</p>
 */
public final class DWPayloads {
    public static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(DreamWorld.MOD_ID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private DWPayloads() {
    }

    public static void register() {
        CHANNEL.registerMessage(0, DreamStateMessage.class, DreamStateMessage::encode,
                DreamStateMessage::new, DreamStateMessage::handle);
        CHANNEL.registerMessage(1, GraffitiListMessage.class, GraffitiListMessage::encode,
                GraffitiListMessage::new, GraffitiListMessage::handle);
        CHANNEL.registerMessage(2, DreamPatchMessage.class, DreamPatchMessage::encode,
                DreamPatchMessage::new, DreamPatchMessage::handle);
        CHANNEL.registerMessage(3, DreamEntitiesMessage.class, DreamEntitiesMessage::encode,
                DreamEntitiesMessage::new, DreamEntitiesMessage::handle);
        CHANNEL.registerMessage(4, PetalShapeMessage.class, PetalShapeMessage::encode,
                PetalShapeMessage::new, PetalShapeMessage::handle);
        CHANNEL.registerMessage(5, BlastParticlesMessage.class, BlastParticlesMessage::encode,
                BlastParticlesMessage::new, BlastParticlesMessage::handle);
        CHANNEL.registerMessage(6, AnchorStateMessage.class, AnchorStateMessage::encode,
                AnchorStateMessage::new, AnchorStateMessage::handle);
        CHANNEL.registerMessage(7, DreamersMessage.class, DreamersMessage::encode,
                DreamersMessage::new, DreamersMessage::handle);
        CHANNEL.registerMessage(8, TunnelAttachMessage.class, TunnelAttachMessage::encode,
                TunnelAttachMessage::new, TunnelAttachMessage::handle,
                java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    /** Turns one shared packet into its 1.18.2 wire record. */
    public static Object message(DreamPacket packet) {
        if (packet instanceof DreamPackets.DreamState state) {
            return new DreamStateMessage(state.dreaming());
        }
        if (packet instanceof DreamPackets.GraffitiList list) {
            return new GraffitiListMessage(list.positions(), list.maxTunnelsPerGraffiti());
        }
        if (packet instanceof DreamPackets.DreamPatch patch) {
            return new DreamPatchMessage(patch.chunkPos(), patch.entries());
        }
        if (packet instanceof DreamPackets.DreamEntities entities) {
            return new DreamEntitiesMessage(entities.ids());
        }
        if (packet instanceof DreamPackets.PetalShape petal) {
            return new PetalShapeMessage(petal.entityType(), petal.x(), petal.y(), petal.z(),
                    petal.yRot(), petal.count());
        }
        if (packet instanceof DreamPackets.BlastParticles blast) {
            return new BlastParticlesMessage(blast.x(), blast.y(), blast.z(), blast.radius(),
                    blast.count());
        }
        if (packet instanceof DreamPackets.AnchorState anchor) {
            return new AnchorStateMessage(anchor.realSideBound(), anchor.dreamSideBound());
        }
        if (packet instanceof DreamPackets.Dreamers dreamers) {
            return new DreamersMessage(List.copyOf(dreamers.dreamers()));
        }
        if (packet instanceof DreamPackets.TunnelAttach attach) {
            return new TunnelAttachMessage(attach.from(), attach.to());
        }
        throw new IllegalArgumentException("Unknown DreamWorld packet: " + packet.getClass());
    }

    public static void sendToPlayer(ServerPlayer player, DreamPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message(packet));
    }

    public static void sendToServer(DreamPacket packet) {
        CHANNEL.sendToServer(message(packet));
    }

    /** Runs a client-bound handler on the game thread. */
    private static void enqueue(Supplier<NetworkEvent.Context> supplier, Runnable task) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(task);
        context.setPacketHandled(true);
    }

    public record DreamStateMessage(boolean dreaming) {
        public static void encode(DreamStateMessage message, FriendlyByteBuf buffer) {
            buffer.writeBoolean(message.dreaming());
        }

        public DreamStateMessage(FriendlyByteBuf buffer) {
            this(buffer.readBoolean());
        }

        public static void handle(DreamStateMessage message, Supplier<NetworkEvent.Context> context) {
            enqueue(context, () -> DWClientPayloadHandler.handleDreamState(
                    new DreamPackets.DreamState(message.dreaming())));
        }
    }

    public record GraffitiListMessage(List<BlockPos> positions, int maxTunnelsPerGraffiti) {
        public static void encode(GraffitiListMessage message, FriendlyByteBuf buffer) {
            buffer.writeVarInt(message.positions().size());
            for (BlockPos pos : message.positions()) {
                buffer.writeBlockPos(pos);
            }
            buffer.writeVarInt(message.maxTunnelsPerGraffiti());
        }

        public GraffitiListMessage(FriendlyByteBuf buffer) {
            this(readPositions(buffer), buffer.readVarInt());
        }

        private static List<BlockPos> readPositions(FriendlyByteBuf buffer) {
            int size = buffer.readVarInt();
            List<BlockPos> positions = new ArrayList<>(size);
            for (int index = 0; index < size; index++) {
                positions.add(buffer.readBlockPos());
            }
            return positions;
        }

        public static void handle(GraffitiListMessage message, Supplier<NetworkEvent.Context> context) {
            enqueue(context, () -> DWClientPayloadHandler.handleGraffitiList(
                    new DreamPackets.GraffitiList(message.positions(),
                            message.maxTunnelsPerGraffiti())));
        }
    }

    public record DreamPatchMessage(long chunkPos, List<DreamPackets.PatchEntry> entries) {
        public static void encode(DreamPatchMessage message, FriendlyByteBuf buffer) {
            buffer.writeLong(message.chunkPos());
            buffer.writeVarInt(message.entries().size());
            for (DreamPackets.PatchEntry entry : message.entries()) {
                buffer.writeLong(entry.pos());
                buffer.writeNbt(entry.state());
                buffer.writeNbt(entry.realState());
                buffer.writeBoolean(entry.intruded());
            }
        }

        public DreamPatchMessage(FriendlyByteBuf buffer) {
            this(buffer.readLong(), readEntries(buffer));
        }

        private static List<DreamPackets.PatchEntry> readEntries(FriendlyByteBuf buffer) {
            int size = buffer.readVarInt();
            List<DreamPackets.PatchEntry> entries = new ArrayList<>(size);
            for (int index = 0; index < size; index++) {
                long pos = buffer.readLong();
                CompoundTag state = buffer.readNbt();
                CompoundTag realState = buffer.readNbt();
                boolean intruded = buffer.readBoolean();
                entries.add(new DreamPackets.PatchEntry(pos, state, realState, intruded));
            }
            return entries;
        }

        public static void handle(DreamPatchMessage message, Supplier<NetworkEvent.Context> context) {
            enqueue(context, () -> DWClientPayloadHandler.handleDreamPatch(
                    new DreamPackets.DreamPatch(message.chunkPos(), message.entries())));
        }
    }

    public record DreamEntitiesMessage(List<Integer> ids) {
        public static void encode(DreamEntitiesMessage message, FriendlyByteBuf buffer) {
            buffer.writeVarInt(message.ids().size());
            for (int id : message.ids()) {
                buffer.writeVarInt(id);
            }
        }

        public DreamEntitiesMessage(FriendlyByteBuf buffer) {
            this(readIds(buffer));
        }

        private static List<Integer> readIds(FriendlyByteBuf buffer) {
            int size = buffer.readVarInt();
            List<Integer> ids = new ArrayList<>(size);
            for (int index = 0; index < size; index++) {
                ids.add(buffer.readVarInt());
            }
            return ids;
        }

        public static void handle(DreamEntitiesMessage message, Supplier<NetworkEvent.Context> context) {
            enqueue(context, () -> DWClientPayloadHandler.handleDreamEntities(
                    new DreamPackets.DreamEntities(message.ids())));
        }
    }

    public record PetalShapeMessage(ResourceLocation entityType, double x, double y, double z,
            float yRot, int count) {
        public static void encode(PetalShapeMessage message, FriendlyByteBuf buffer) {
            buffer.writeResourceLocation(message.entityType());
            buffer.writeDouble(message.x());
            buffer.writeDouble(message.y());
            buffer.writeDouble(message.z());
            buffer.writeFloat(message.yRot());
            buffer.writeVarInt(message.count());
        }

        public PetalShapeMessage(FriendlyByteBuf buffer) {
            this(buffer.readResourceLocation(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readDouble(), buffer.readFloat(), buffer.readVarInt());
        }

        public static void handle(PetalShapeMessage message, Supplier<NetworkEvent.Context> context) {
            enqueue(context, () -> DWClientPayloadHandler.handlePetalShape(
                    new DreamPackets.PetalShape(message.entityType(), message.x(), message.y(),
                            message.z(), message.yRot(), message.count())));
        }
    }

    public record BlastParticlesMessage(double x, double y, double z, float radius, int count) {
        public static void encode(BlastParticlesMessage message, FriendlyByteBuf buffer) {
            buffer.writeDouble(message.x());
            buffer.writeDouble(message.y());
            buffer.writeDouble(message.z());
            buffer.writeFloat(message.radius());
            buffer.writeVarInt(message.count());
        }

        public BlastParticlesMessage(FriendlyByteBuf buffer) {
            this(buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readFloat(), buffer.readVarInt());
        }

        public static void handle(BlastParticlesMessage message,
                Supplier<NetworkEvent.Context> context) {
            enqueue(context, () -> DWClientPayloadHandler.handleBlastParticles(
                    new DreamPackets.BlastParticles(message.x(), message.y(), message.z(),
                            message.radius(), message.count())));
        }
    }

    public record AnchorStateMessage(boolean realSideBound, boolean dreamSideBound) {
        public static void encode(AnchorStateMessage message, FriendlyByteBuf buffer) {
            buffer.writeBoolean(message.realSideBound());
            buffer.writeBoolean(message.dreamSideBound());
        }

        public AnchorStateMessage(FriendlyByteBuf buffer) {
            this(buffer.readBoolean(), buffer.readBoolean());
        }

        public static void handle(AnchorStateMessage message, Supplier<NetworkEvent.Context> context) {
            enqueue(context, () -> DWClientPayloadHandler.handleAnchorState(
                    new DreamPackets.AnchorState(message.realSideBound(), message.dreamSideBound())));
        }
    }

    public record DreamersMessage(List<UUID> dreamers) {
        public static void encode(DreamersMessage message, FriendlyByteBuf buffer) {
            buffer.writeVarInt(message.dreamers().size());
            for (UUID dreamer : message.dreamers()) {
                buffer.writeUUID(dreamer);
            }
        }

        public DreamersMessage(FriendlyByteBuf buffer) {
            this(readDreamers(buffer));
        }

        private static List<UUID> readDreamers(FriendlyByteBuf buffer) {
            int size = buffer.readVarInt();
            List<UUID> dreamers = new ArrayList<>(size);
            for (int index = 0; index < size; index++) {
                dreamers.add(buffer.readUUID());
            }
            return dreamers;
        }

        public static void handle(DreamersMessage message, Supplier<NetworkEvent.Context> context) {
            enqueue(context, () -> DWClientPayloadHandler.handleDreamers(
                    new DreamPackets.Dreamers(message.dreamers())));
        }
    }

    public record TunnelAttachMessage(BlockPos from, BlockPos to) {
        public static void encode(TunnelAttachMessage message, FriendlyByteBuf buffer) {
            buffer.writeBlockPos(message.from());
            buffer.writeBlockPos(message.to());
        }

        public TunnelAttachMessage(FriendlyByteBuf buffer) {
            this(buffer.readBlockPos(), buffer.readBlockPos());
        }

        public static void handle(TunnelAttachMessage message,
                Supplier<NetworkEvent.Context> context) {
            NetworkEvent.Context eventContext = context.get();
            eventContext.enqueueWork(() -> {
                ServerPlayer player = eventContext.getSender();
                if (player == null) {
                    return;
                }
                if (player.getVehicle() instanceof Boat boat
                        && boat instanceof DreamTunnelBoat tunnelBoat) {
                    tunnelBoat.attachDreamTunnel(
                            new TunnelGeometry.Pair(message.from(), message.to()));
                }
            });
            eventContext.setPacketHandled(true);
        }
    }
}
