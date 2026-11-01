package cc.sighs.dreamworld.platform;

import cc.sighs.dreamworld.net.DreamPackets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Wire representation for the 1.20.1 channel.
 *
 * <p>1.20.1 has no payload/codec API: every message is a plain object encoded by hand into a
 * {@link FriendlyByteBuf} and registered with an index on the mod's {@code SimpleChannel}. The
 * records mirror {@link DreamPackets}, so the shared handlers never see a loader type.</p>
 */
public final class ForgeMessages {
    private ForgeMessages() {
    }

    public record DreamState(boolean dreaming) {
        public static void encode(DreamState message, FriendlyByteBuf buffer) {
            buffer.writeBoolean(message.dreaming());
        }

        public static DreamState decode(FriendlyByteBuf buffer) {
            return new DreamState(buffer.readBoolean());
        }

        public DreamPackets.DreamState common() {
            return new DreamPackets.DreamState(dreaming);
        }
    }

    public record GraffitiList(List<BlockPos> positions, int maxTunnelsPerGraffiti) {
        public static void encode(GraffitiList message, FriendlyByteBuf buffer) {
            buffer.writeVarInt(message.positions().size());
            for (BlockPos pos : message.positions()) {
                buffer.writeBlockPos(pos);
            }
            buffer.writeVarInt(message.maxTunnelsPerGraffiti());
        }

        public static GraffitiList decode(FriendlyByteBuf buffer) {
            int size = buffer.readVarInt();
            List<BlockPos> positions = new ArrayList<>(size);
            for (int index = 0; index < size; index++) {
                positions.add(buffer.readBlockPos());
            }
            return new GraffitiList(positions, buffer.readVarInt());
        }

        public DreamPackets.GraffitiList common() {
            return new DreamPackets.GraffitiList(positions, maxTunnelsPerGraffiti);
        }
    }

    public record PatchEntry(long pos, CompoundTag state, CompoundTag realState, boolean intruded) {
        static void encode(PatchEntry entry, FriendlyByteBuf buffer) {
            buffer.writeVarLong(entry.pos());
            buffer.writeNbt(entry.state());
            buffer.writeNbt(entry.realState());
            buffer.writeBoolean(entry.intruded());
        }

        static PatchEntry decode(FriendlyByteBuf buffer) {
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

    public record DreamPatch(long chunkPos, List<PatchEntry> entries) {
        public static void encode(DreamPatch message, FriendlyByteBuf buffer) {
            buffer.writeVarLong(message.chunkPos());
            buffer.writeVarInt(message.entries().size());
            for (PatchEntry entry : message.entries()) {
                PatchEntry.encode(entry, buffer);
            }
        }

        public static DreamPatch decode(FriendlyByteBuf buffer) {
            long chunkPos = buffer.readVarLong();
            int size = buffer.readVarInt();
            List<PatchEntry> entries = new ArrayList<>(size);
            for (int index = 0; index < size; index++) {
                entries.add(PatchEntry.decode(buffer));
            }
            return new DreamPatch(chunkPos, entries);
        }

        public DreamPackets.DreamPatch common() {
            return new DreamPackets.DreamPatch(chunkPos,
                    entries.stream().map(PatchEntry::common).toList());
        }
    }

    public record DreamEntities(List<Integer> ids) {
        public static void encode(DreamEntities message, FriendlyByteBuf buffer) {
            buffer.writeVarInt(message.ids().size());
            for (int id : message.ids()) {
                buffer.writeVarInt(id);
            }
        }

        public static DreamEntities decode(FriendlyByteBuf buffer) {
            int size = buffer.readVarInt();
            List<Integer> ids = new ArrayList<>(size);
            for (int index = 0; index < size; index++) {
                ids.add(buffer.readVarInt());
            }
            return new DreamEntities(ids);
        }

        public DreamPackets.DreamEntities common() {
            return new DreamPackets.DreamEntities(ids);
        }
    }

    public record PetalShape(ResourceLocation entityType, double x, double y, double z, float yRot,
            int count) {
        public static void encode(PetalShape message, FriendlyByteBuf buffer) {
            buffer.writeResourceLocation(message.entityType());
            buffer.writeDouble(message.x());
            buffer.writeDouble(message.y());
            buffer.writeDouble(message.z());
            buffer.writeFloat(message.yRot());
            buffer.writeVarInt(message.count());
        }

        public static PetalShape decode(FriendlyByteBuf buffer) {
            return new PetalShape(buffer.readResourceLocation(), buffer.readDouble(),
                    buffer.readDouble(), buffer.readDouble(), buffer.readFloat(),
                    buffer.readVarInt());
        }

        public DreamPackets.PetalShape common() {
            return new DreamPackets.PetalShape(entityType, x, y, z, yRot, count);
        }
    }

    public record BlastParticles(double x, double y, double z, float radius, int count) {
        public static void encode(BlastParticles message, FriendlyByteBuf buffer) {
            buffer.writeDouble(message.x());
            buffer.writeDouble(message.y());
            buffer.writeDouble(message.z());
            buffer.writeFloat(message.radius());
            buffer.writeVarInt(message.count());
        }

        public static BlastParticles decode(FriendlyByteBuf buffer) {
            return new BlastParticles(buffer.readDouble(), buffer.readDouble(),
                    buffer.readDouble(), buffer.readFloat(), buffer.readVarInt());
        }

        public DreamPackets.BlastParticles common() {
            return new DreamPackets.BlastParticles(x, y, z, radius, count);
        }
    }

    public record AnchorState(boolean bound) {
        public static void encode(AnchorState message, FriendlyByteBuf buffer) {
            buffer.writeBoolean(message.bound());
        }

        public static AnchorState decode(FriendlyByteBuf buffer) {
            return new AnchorState(buffer.readBoolean());
        }

        public DreamPackets.AnchorState common() {
            return new DreamPackets.AnchorState(bound);
        }
    }

    public record Dreamers(List<UUID> dreamers) {
        public static void encode(Dreamers message, FriendlyByteBuf buffer) {
            buffer.writeVarInt(message.dreamers().size());
            for (UUID dreamer : message.dreamers()) {
                buffer.writeUUID(dreamer);
            }
        }

        public static Dreamers decode(FriendlyByteBuf buffer) {
            int size = buffer.readVarInt();
            List<UUID> dreamers = new ArrayList<>(size);
            for (int index = 0; index < size; index++) {
                dreamers.add(buffer.readUUID());
            }
            return new Dreamers(dreamers);
        }

        public DreamPackets.Dreamers common() {
            return new DreamPackets.Dreamers(dreamers);
        }
    }

    public record TunnelAttach(BlockPos from, BlockPos to) {
        public static void encode(TunnelAttach message, FriendlyByteBuf buffer) {
            buffer.writeBlockPos(message.from());
            buffer.writeBlockPos(message.to());
        }

        public static TunnelAttach decode(FriendlyByteBuf buffer) {
            return new TunnelAttach(buffer.readBlockPos(), buffer.readBlockPos());
        }

        public DreamPackets.TunnelAttach common() {
            return new DreamPackets.TunnelAttach(from, to);
        }
    }
}
