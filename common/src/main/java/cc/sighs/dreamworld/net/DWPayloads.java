package cc.sighs.dreamworld.net;

import cc.sighs.dreamworld.DreamWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Wire representation shared by the 1.21.1 family of targets.
 *
 * <p>The payloads are vanilla {@link CustomPacketPayload}s; only the registration and the actual
 * sending differ per loader, which is why this file lives in the shared tree. Loaders older than
 * 1.20.5 have no payload API and replace this file with their own channel implementation.</p>
 */
public final class DWPayloads {
    private DWPayloads() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(DreamWorld.MOD_ID, path);
    }

    public record DreamStatePayload(boolean dreaming) implements CustomPacketPayload {
        public static final Type<DreamStatePayload> TYPE = new Type<>(id("dream_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DreamStatePayload> STREAM_CODEC =
                StreamCodec.of((buffer, payload) -> buffer.writeBoolean(payload.dreaming()),
                        buffer -> new DreamStatePayload(buffer.readBoolean()));

        public DreamPackets.DreamState common() {
            return new DreamPackets.DreamState(dreaming);
        }

        @Override
        public Type<DreamStatePayload> type() {
            return TYPE;
        }
    }

    public record GraffitiListPayload(List<BlockPos> positions, int maxTunnelsPerGraffiti)
            implements CustomPacketPayload {
        public static final Type<GraffitiListPayload> TYPE = new Type<>(id("graffiti_list"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GraffitiListPayload> STREAM_CODEC =
                StreamCodec.composite(BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()),
                        GraffitiListPayload::positions,
                        ByteBufCodecs.VAR_INT, GraffitiListPayload::maxTunnelsPerGraffiti,
                        GraffitiListPayload::new);

        public DreamPackets.GraffitiList common() {
            return new DreamPackets.GraffitiList(positions, maxTunnelsPerGraffiti);
        }

        @Override
        public Type<GraffitiListPayload> type() {
            return TYPE;
        }
    }

    public record PatchEntry(long pos, CompoundTag state, CompoundTag realState, boolean intruded) {
        public static final StreamCodec<RegistryFriendlyByteBuf, PatchEntry> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_LONG, PatchEntry::pos,
                        ByteBufCodecs.COMPOUND_TAG, PatchEntry::state,
                        ByteBufCodecs.COMPOUND_TAG, PatchEntry::realState,
                        ByteBufCodecs.BOOL, PatchEntry::intruded, PatchEntry::new);

        public DreamPackets.PatchEntry common() {
            return new DreamPackets.PatchEntry(pos, state, realState, intruded);
        }

        public static PatchEntry of(DreamPackets.PatchEntry entry) {
            return new PatchEntry(entry.pos(), entry.state(), entry.realState(), entry.intruded());
        }
    }

    public record DreamPatchPayload(long chunkPos, List<PatchEntry> entries)
            implements CustomPacketPayload {
        public static final Type<DreamPatchPayload> TYPE = new Type<>(id("dream_patch"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DreamPatchPayload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_LONG, DreamPatchPayload::chunkPos,
                        PatchEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), DreamPatchPayload::entries,
                        DreamPatchPayload::new);

        public DreamPackets.DreamPatch common() {
            return new DreamPackets.DreamPatch(chunkPos,
                    entries.stream().map(PatchEntry::common).toList());
        }

        @Override
        public Type<DreamPatchPayload> type() {
            return TYPE;
        }
    }

    public record DreamEntitiesPayload(List<Integer> ids) implements CustomPacketPayload {
        public static final Type<DreamEntitiesPayload> TYPE = new Type<>(id("dream_entities"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DreamEntitiesPayload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()),
                        DreamEntitiesPayload::ids, DreamEntitiesPayload::new);

        public DreamPackets.DreamEntities common() {
            return new DreamPackets.DreamEntities(ids);
        }

        @Override
        public Type<DreamEntitiesPayload> type() {
            return TYPE;
        }
    }

    public record PetalShapePayload(ResourceLocation entityType, double x, double y, double z,
            float yRot, int count) implements CustomPacketPayload {
        public static final Type<PetalShapePayload> TYPE = new Type<>(id("petal_shape"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PetalShapePayload> STREAM_CODEC =
                StreamCodec.composite(ResourceLocation.STREAM_CODEC, PetalShapePayload::entityType,
                        ByteBufCodecs.DOUBLE, PetalShapePayload::x,
                        ByteBufCodecs.DOUBLE, PetalShapePayload::y,
                        ByteBufCodecs.DOUBLE, PetalShapePayload::z,
                        ByteBufCodecs.FLOAT, PetalShapePayload::yRot,
                        ByteBufCodecs.VAR_INT, PetalShapePayload::count,
                        PetalShapePayload::new);

        public DreamPackets.PetalShape common() {
            return new DreamPackets.PetalShape(entityType, x, y, z, yRot, count);
        }

        @Override
        public Type<PetalShapePayload> type() {
            return TYPE;
        }
    }

    public record BlastParticlesPayload(double x, double y, double z, float radius, int count)
            implements CustomPacketPayload {
        public static final Type<BlastParticlesPayload> TYPE = new Type<>(id("blast_particles"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BlastParticlesPayload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.DOUBLE, BlastParticlesPayload::x,
                        ByteBufCodecs.DOUBLE, BlastParticlesPayload::y,
                        ByteBufCodecs.DOUBLE, BlastParticlesPayload::z,
                        ByteBufCodecs.FLOAT, BlastParticlesPayload::radius,
                        ByteBufCodecs.VAR_INT, BlastParticlesPayload::count,
                        BlastParticlesPayload::new);

        public DreamPackets.BlastParticles common() {
            return new DreamPackets.BlastParticles(x, y, z, radius, count);
        }

        @Override
        public Type<BlastParticlesPayload> type() {
            return TYPE;
        }
    }

    public record AnchorStatePayload(boolean bound) implements CustomPacketPayload {
        public static final Type<AnchorStatePayload> TYPE = new Type<>(id("anchor_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AnchorStatePayload> STREAM_CODEC =
                StreamCodec.of((buffer, payload) -> buffer.writeBoolean(payload.bound()),
                        buffer -> new AnchorStatePayload(buffer.readBoolean()));

        public DreamPackets.AnchorState common() {
            return new DreamPackets.AnchorState(bound);
        }

        @Override
        public Type<AnchorStatePayload> type() {
            return TYPE;
        }
    }

    public record DreamersPayload(List<UUID> dreamers) implements CustomPacketPayload {
        public static final Type<DreamersPayload> TYPE = new Type<>(id("dreamers"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DreamersPayload> STREAM_CODEC =
                StreamCodec.of((buffer, payload) -> {
                    buffer.writeVarInt(payload.dreamers().size());
                    for (UUID dreamer : payload.dreamers()) {
                        buffer.writeUUID(dreamer);
                    }
                }, buffer -> {
                    int size = buffer.readVarInt();
                    List<UUID> dreamers = new ArrayList<>(size);
                    for (int index = 0; index < size; index++) {
                        dreamers.add(buffer.readUUID());
                    }
                    return new DreamersPayload(dreamers);
                });

        public DreamPackets.Dreamers common() {
            return new DreamPackets.Dreamers(dreamers);
        }

        @Override
        public Type<DreamersPayload> type() {
            return TYPE;
        }
    }

    public record TunnelAttachPayload(BlockPos from, BlockPos to) implements CustomPacketPayload {
        public static final Type<TunnelAttachPayload> TYPE = new Type<>(id("tunnel_attach"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TunnelAttachPayload> STREAM_CODEC =
                StreamCodec.composite(BlockPos.STREAM_CODEC, TunnelAttachPayload::from,
                        BlockPos.STREAM_CODEC, TunnelAttachPayload::to, TunnelAttachPayload::new);

        public DreamPackets.TunnelAttach common() {
            return new DreamPackets.TunnelAttach(from, to);
        }

        @Override
        public Type<TunnelAttachPayload> type() {
            return TYPE;
        }
    }
}
