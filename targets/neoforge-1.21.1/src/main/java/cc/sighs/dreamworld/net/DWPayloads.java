package cc.sighs.dreamworld.net;

import cc.sighs.dreamworld.DreamWorldNeoForge121;
import cc.sighs.dreamworld.boat.DreamTunnelBoat;
import cc.sighs.dreamworld.boat.TunnelGeometry;
import cc.sighs.dreamworld.client.DWClientPayloadHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.vehicle.Boat;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Network payloads of the mod: the dream flag, the graffiti list and tunnel limit, the dream patch
 * set of a chunk, the dreamer list, and the request to attach a boat to a tunnel.
 */
@EventBusSubscriber(modid = DreamWorldNeoForge121.MOD_ID)
public final class DWPayloads {
    private DWPayloads() {
    }

    /** Tells the owning client that it entered or left the dream world. */
    public record DreamStatePayload(boolean dreaming) implements CustomPacketPayload {
        public static final Type<DreamStatePayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(DreamWorldNeoForge121.MOD_ID, "dream_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DreamStatePayload> STREAM_CODEC =
                StreamCodec.of((buffer, payload) -> buffer.writeBoolean(payload.dreaming()),
                        buffer -> new DreamStatePayload(buffer.readBoolean()));

        @Override
        public Type<DreamStatePayload> type() {
            return TYPE;
        }
    }

    /**
     * Full list of graffiti coordinates the client needs to render tunnels, plus the server's limit
     * of tunnels per graffiti so both sides link exactly the same pairs.
     */
    public record GraffitiListPayload(List<BlockPos> positions, int maxTunnelsPerGraffiti)
            implements CustomPacketPayload {
        public static final Type<GraffitiListPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(DreamWorldNeoForge121.MOD_ID, "graffiti_list"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GraffitiListPayload> STREAM_CODEC =
                StreamCodec.composite(BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()),
                        GraffitiListPayload::positions,
                        ByteBufCodecs.VAR_INT, GraffitiListPayload::maxTunnelsPerGraffiti,
                        GraffitiListPayload::new);

        @Override
        public Type<GraffitiListPayload> type() {
            return TYPE;
        }
    }

    /**
     * One patched position: the block state the dream layer shows there, the real state the overlay
     * hides, and whether the dream block was carried in by an intrusion. An intruded block keeps the
     * colour of the layer it came from, so the client needs to know about it when tinting.
     */
    public record PatchEntry(long pos, CompoundTag state, CompoundTag realState, boolean intruded) {
        public static final StreamCodec<RegistryFriendlyByteBuf, PatchEntry> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_LONG, PatchEntry::pos,
                        ByteBufCodecs.COMPOUND_TAG, PatchEntry::state,
                        ByteBufCodecs.COMPOUND_TAG, PatchEntry::realState,
                        ByteBufCodecs.BOOL, PatchEntry::intruded, PatchEntry::new);
    }

    /** Dream patch set of one chunk, pushed to dreaming clients. */
    public record DreamPatchPayload(long chunkPos, List<PatchEntry> entries) implements CustomPacketPayload {
        public static final Type<DreamPatchPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(DreamWorldNeoForge121.MOD_ID, "dream_patch"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DreamPatchPayload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_LONG, DreamPatchPayload::chunkPos,
                        PatchEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), DreamPatchPayload::entries,
                        DreamPatchPayload::new);

        @Override
        public Type<DreamPatchPayload> type() {
            return TYPE;
        }
    }

    /** IDs of the dream-only entities the client is allowed to render. */
    public record DreamEntitiesPayload(List<Integer> ids) implements CustomPacketPayload {
        public static final Type<DreamEntitiesPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(DreamWorldNeoForge121.MOD_ID, "dream_entities"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DreamEntitiesPayload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()),
                        DreamEntitiesPayload::ids, DreamEntitiesPayload::new);

        @Override
        public Type<DreamEntitiesPayload> type() {
            return TYPE;
        }
    }

    /**
     * Asks the clients to leave a petal silhouette of a creature that just crossed the mirror.
     *
     * <p>The shape cannot be computed on the server - the model lives in the client renderer - so the
     * payload only carries what the client needs to rebuild it: the entity type, where it stood and
     * how it was turned.</p>
     */
    public record PetalShapePayload(ResourceLocation entityType, double x, double y, double z,
            float yRot, int count) implements CustomPacketPayload {
        public static final Type<PetalShapePayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(DreamWorldNeoForge121.MOD_ID, "petal_shape"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PetalShapePayload> STREAM_CODEC =
                StreamCodec.composite(ResourceLocation.STREAM_CODEC, PetalShapePayload::entityType,
                        ByteBufCodecs.DOUBLE, PetalShapePayload::x,
                        ByteBufCodecs.DOUBLE, PetalShapePayload::y,
                        ByteBufCodecs.DOUBLE, PetalShapePayload::z,
                        ByteBufCodecs.FLOAT, PetalShapePayload::yRot,
                        ByteBufCodecs.VAR_INT, PetalShapePayload::count,
                        PetalShapePayload::new);

        @Override
        public Type<PetalShapePayload> type() {
            return TYPE;
        }
    }

    /**
     * A petal burst for the wand blast.
     *
     * <p>Vanilla's {@code ClientboundLevelParticlesPacket} gives every particle a gaussian velocity
     * that is unrelated to where it spawned, which reads as a cloud. The burst is therefore a custom
     * payload: the client spawns the petals itself, each one flying away from the centre along its
     * own spawn direction.</p>
     */
    public record BlastParticlesPayload(double x, double y, double z, float radius, int count)
            implements CustomPacketPayload {
        public static final Type<BlastParticlesPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(DreamWorldNeoForge121.MOD_ID, "blast_particles"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BlastParticlesPayload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.DOUBLE, BlastParticlesPayload::x,
                        ByteBufCodecs.DOUBLE, BlastParticlesPayload::y,
                        ByteBufCodecs.DOUBLE, BlastParticlesPayload::z,
                        ByteBufCodecs.FLOAT, BlastParticlesPayload::radius,
                        ByteBufCodecs.VAR_INT, BlastParticlesPayload::count,
                        BlastParticlesPayload::new);

        @Override
        public Type<BlastParticlesPayload> type() {
            return TYPE;
        }
    }

    /** Whether this player currently has a dream alloy spear anchored somewhere. */
    public record AnchorStatePayload(boolean bound) implements CustomPacketPayload {
        public static final Type<AnchorStatePayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(DreamWorldNeoForge121.MOD_ID, "anchor_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AnchorStatePayload> STREAM_CODEC =
                StreamCodec.of((buffer, payload) -> buffer.writeBoolean(payload.bound()),
                        buffer -> new AnchorStatePayload(buffer.readBoolean()));

        @Override
        public Type<AnchorStatePayload> type() {
            return TYPE;
        }
    }

    /**
     * UUIDs of the players currently dreaming.
     *
     * <p>Only other clients need this: the dreamer's body is not actually asleep anywhere, it is a
     * normal standing player that the dreamer's own client and the server see as such. Everybody
     * else renders that player lying down, which is why the set is broadcast rather than tied to
     * the entity data of a single player.</p>
     */
    public record DreamersPayload(List<UUID> dreamers) implements CustomPacketPayload {
        public static final Type<DreamersPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(DreamWorldNeoForge121.MOD_ID, "dreamers"));
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

        @Override
        public Type<DreamersPayload> type() {
            return TYPE;
        }
    }

    /** Asks the server to attach the sender's boat to the named tunnel. */
    public record TunnelAttachPayload(BlockPos from, BlockPos to) implements CustomPacketPayload {
        public static final Type<TunnelAttachPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(DreamWorldNeoForge121.MOD_ID, "tunnel_attach"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TunnelAttachPayload> STREAM_CODEC =
                StreamCodec.composite(BlockPos.STREAM_CODEC, TunnelAttachPayload::from,
                        BlockPos.STREAM_CODEC, TunnelAttachPayload::to, TunnelAttachPayload::new);

        @Override
        public Type<TunnelAttachPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(TunnelAttachPayload.TYPE, TunnelAttachPayload.STREAM_CODEC,
                DWPayloads::handleTunnelAttach);
        registrar.playToClient(DreamStatePayload.TYPE, DreamStatePayload.STREAM_CODEC,
                new DirectionalPayloadHandler<>(DWClientPayloadHandler::handleDreamState,
                        (payload, context) -> {
                        }));
        registrar.playToClient(DreamersPayload.TYPE, DreamersPayload.STREAM_CODEC,
                new DirectionalPayloadHandler<>(DWClientPayloadHandler::handleDreamers,
                        (payload, context) -> {
                        }));
        registrar.playToClient(BlastParticlesPayload.TYPE, BlastParticlesPayload.STREAM_CODEC,
                new DirectionalPayloadHandler<>(DWClientPayloadHandler::handleBlastParticles,
                        (payload, context) -> {
                        }));
        registrar.playToClient(PetalShapePayload.TYPE, PetalShapePayload.STREAM_CODEC,
                new DirectionalPayloadHandler<>(DWClientPayloadHandler::handlePetalShape,
                        (payload, context) -> {
                        }));
        registrar.playToClient(AnchorStatePayload.TYPE, AnchorStatePayload.STREAM_CODEC,
                new DirectionalPayloadHandler<>(DWClientPayloadHandler::handleAnchorState,
                        (payload, context) -> {
                        }));
        registrar.playToClient(GraffitiListPayload.TYPE, GraffitiListPayload.STREAM_CODEC,
                new DirectionalPayloadHandler<>(DWClientPayloadHandler::handleGraffitiList,
                        (payload, context) -> {
                        }));
        registrar.playToClient(DreamEntitiesPayload.TYPE, DreamEntitiesPayload.STREAM_CODEC,
                new DirectionalPayloadHandler<>(DWClientPayloadHandler::handleDreamEntities,
                        (payload, context) -> {
                        }));
        registrar.playToClient(DreamPatchPayload.TYPE, DreamPatchPayload.STREAM_CODEC,
                new DirectionalPayloadHandler<>(DWClientPayloadHandler::handleDreamPatch,
                        (payload, context) -> {
                        }));
    }

    /**
     * Attaches the sender's boat to the tunnel it is parked next to.
     *
     * <p>The decision is the server's: the boat only attaches when the sender still rides it, is
     * dreaming, and is close enough to a tunnel. The client sends the request and nothing else.</p>
     */
    public static void handleTunnelAttach(TunnelAttachPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (player.getVehicle() instanceof Boat boat
                    && boat instanceof DreamTunnelBoat tunnelBoat) {
                tunnelBoat.attachDreamTunnel(
                        new TunnelGeometry.Pair(payload.from(), payload.to()));
            }
        });
    }
}
