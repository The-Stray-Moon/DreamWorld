package cc.sighs.dreamworld.net;

import cc.sighs.dreamworld.DreamWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Wire representation of the mod messages for Minecraft 1.20.1.
 *
 * <p>1.20.1 has no vanilla payload API, so the packets use Fabric API's {@link FabricPacket} channel
 * codec instead of the {@code CustomPacketPayload}/{@code StreamCodec} of the shared tree. The record
 * shapes and the {@code common()} conversions are the same as the shared file, which is why this
 * replacement only changes the encoding.</p>
 */
public final class DWPayloads {
    private DWPayloads() {
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(DreamWorld.MOD_ID, path);
    }

    public record DreamStatePayload(boolean dreaming) implements FabricPacket {
        public static final PacketType<DreamStatePayload> TYPE =
                PacketType.create(id("dream_state"), buffer -> new DreamStatePayload(buffer.readBoolean()));

        @Override
        public void write(FriendlyByteBuf buffer) {
            buffer.writeBoolean(dreaming);
        }

        @Override
        public PacketType<?> getType() {
            return TYPE;
        }

        public DreamPackets.DreamState common() {
            return new DreamPackets.DreamState(dreaming);
        }
    }

    public record GraffitiListPayload(List<BlockPos> positions, int maxTunnelsPerGraffiti)
            implements FabricPacket {
        public static final PacketType<GraffitiListPayload> TYPE = PacketType.create(id("graffiti_list"),
                buffer -> {
                    int size = buffer.readVarInt();
                    List<BlockPos> positions = new ArrayList<>(size);
                    for (int index = 0; index < size; index++) {
                        positions.add(buffer.readBlockPos());
                    }
                    return new GraffitiListPayload(positions, buffer.readVarInt());
                });

        @Override
        public void write(FriendlyByteBuf buffer) {
            buffer.writeVarInt(positions.size());
            for (BlockPos pos : positions) {
                buffer.writeBlockPos(pos);
            }
            buffer.writeVarInt(maxTunnelsPerGraffiti);
        }

        @Override
        public PacketType<?> getType() {
            return TYPE;
        }

        public DreamPackets.GraffitiList common() {
            return new DreamPackets.GraffitiList(positions, maxTunnelsPerGraffiti);
        }
    }

    public record PatchEntry(long pos, CompoundTag state, CompoundTag realState, boolean intruded) {
        public void write(FriendlyByteBuf buffer) {
            buffer.writeVarLong(pos);
            buffer.writeNbt(state);
            buffer.writeNbt(realState);
            buffer.writeBoolean(intruded);
        }

        public static PatchEntry read(FriendlyByteBuf buffer) {
            return new PatchEntry(buffer.readVarLong(), buffer.readNbt(), buffer.readNbt(),
                    buffer.readBoolean());
        }

        public DreamPackets.PatchEntry common() {
            return new DreamPackets.PatchEntry(pos, state, realState, intruded);
        }

        public static PatchEntry of(DreamPackets.PatchEntry entry) {
            return new PatchEntry(entry.pos(), entry.state(), entry.realState(), entry.intruded());
        }
    }

    public record DreamPatchPayload(long chunkPos, List<PatchEntry> entries) implements FabricPacket {
        public static final PacketType<DreamPatchPayload> TYPE = PacketType.create(id("dream_patch"),
                buffer -> {
                    long chunkPos = buffer.readVarLong();
                    int size = buffer.readVarInt();
                    List<PatchEntry> entries = new ArrayList<>(size);
                    for (int index = 0; index < size; index++) {
                        entries.add(PatchEntry.read(buffer));
                    }
                    return new DreamPatchPayload(chunkPos, entries);
                });

        @Override
        public void write(FriendlyByteBuf buffer) {
            buffer.writeVarLong(chunkPos);
            buffer.writeVarInt(entries.size());
            for (PatchEntry entry : entries) {
                entry.write(buffer);
            }
        }

        @Override
        public PacketType<?> getType() {
            return TYPE;
        }

        public DreamPackets.DreamPatch common() {
            return new DreamPackets.DreamPatch(chunkPos, entries.stream().map(PatchEntry::common).toList());
        }
    }

    public record DreamEntitiesPayload(List<Integer> ids) implements FabricPacket {
        public static final PacketType<DreamEntitiesPayload> TYPE = PacketType.create(id("dream_entities"),
                buffer -> {
                    int size = buffer.readVarInt();
                    List<Integer> ids = new ArrayList<>(size);
                    for (int index = 0; index < size; index++) {
                        ids.add(buffer.readVarInt());
                    }
                    return new DreamEntitiesPayload(ids);
                });

        @Override
        public void write(FriendlyByteBuf buffer) {
            buffer.writeVarInt(ids.size());
            for (int id : ids) {
                buffer.writeVarInt(id);
            }
        }

        @Override
        public PacketType<?> getType() {
            return TYPE;
        }

        public DreamPackets.DreamEntities common() {
            return new DreamPackets.DreamEntities(ids);
        }
    }

    public record PetalShapePayload(ResourceLocation entityType, double x, double y, double z,
            float yRot, int count) implements FabricPacket {
        public static final PacketType<PetalShapePayload> TYPE = PacketType.create(id("petal_shape"),
                buffer -> new PetalShapePayload(buffer.readResourceLocation(), buffer.readDouble(),
                        buffer.readDouble(), buffer.readDouble(), buffer.readFloat(),
                        buffer.readVarInt()));

        @Override
        public void write(FriendlyByteBuf buffer) {
            buffer.writeResourceLocation(entityType);
            buffer.writeDouble(x);
            buffer.writeDouble(y);
            buffer.writeDouble(z);
            buffer.writeFloat(yRot);
            buffer.writeVarInt(count);
        }

        @Override
        public PacketType<?> getType() {
            return TYPE;
        }

        public DreamPackets.PetalShape common() {
            return new DreamPackets.PetalShape(entityType, x, y, z, yRot, count);
        }
    }

    public record BlastParticlesPayload(double x, double y, double z, float radius, int count)
            implements FabricPacket {
        public static final PacketType<BlastParticlesPayload> TYPE = PacketType.create(
                id("blast_particles"),
                buffer -> new BlastParticlesPayload(buffer.readDouble(), buffer.readDouble(),
                        buffer.readDouble(), buffer.readFloat(), buffer.readVarInt()));

        @Override
        public void write(FriendlyByteBuf buffer) {
            buffer.writeDouble(x);
            buffer.writeDouble(y);
            buffer.writeDouble(z);
            buffer.writeFloat(radius);
            buffer.writeVarInt(count);
        }

        @Override
        public PacketType<?> getType() {
            return TYPE;
        }

        public DreamPackets.BlastParticles common() {
            return new DreamPackets.BlastParticles(x, y, z, radius, count);
        }
    }

    public record AnchorStatePayload(boolean realSideBound, boolean dreamSideBound)
            implements FabricPacket {
        public static final PacketType<AnchorStatePayload> TYPE = PacketType.create(id("anchor_state"),
                buffer -> new AnchorStatePayload(buffer.readBoolean(), buffer.readBoolean()));

        @Override
        public void write(FriendlyByteBuf buffer) {
            buffer.writeBoolean(realSideBound);
            buffer.writeBoolean(dreamSideBound);
        }

        @Override
        public PacketType<?> getType() {
            return TYPE;
        }

        public DreamPackets.AnchorState common() {
            return new DreamPackets.AnchorState(realSideBound, dreamSideBound);
        }
    }

    public record DreamersPayload(List<UUID> dreamers) implements FabricPacket {
        public static final PacketType<DreamersPayload> TYPE = PacketType.create(id("dreamers"),
                buffer -> {
                    int size = buffer.readVarInt();
                    List<UUID> dreamers = new ArrayList<>(size);
                    for (int index = 0; index < size; index++) {
                        dreamers.add(buffer.readUUID());
                    }
                    return new DreamersPayload(dreamers);
                });

        @Override
        public void write(FriendlyByteBuf buffer) {
            buffer.writeVarInt(dreamers.size());
            for (UUID dreamer : dreamers) {
                buffer.writeUUID(dreamer);
            }
        }

        @Override
        public PacketType<?> getType() {
            return TYPE;
        }

        public DreamPackets.Dreamers common() {
            return new DreamPackets.Dreamers(dreamers);
        }
    }

    public record TunnelAttachPayload(BlockPos from, BlockPos to) implements FabricPacket {
        public static final PacketType<TunnelAttachPayload> TYPE = PacketType.create(id("tunnel_attach"),
                buffer -> new TunnelAttachPayload(buffer.readBlockPos(), buffer.readBlockPos()));

        @Override
        public void write(FriendlyByteBuf buffer) {
            buffer.writeBlockPos(from);
            buffer.writeBlockPos(to);
        }

        @Override
        public PacketType<?> getType() {
            return TYPE;
        }

        public DreamPackets.TunnelAttach common() {
            return new DreamPackets.TunnelAttach(from, to);
        }
    }
}
