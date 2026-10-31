package cc.sighs.dreamworld.net;

import cc.sighs.dreamworld.DreamWorldNeoForge121;
import cc.sighs.dreamworld.client.DWClientPayloadHandler;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Client-bound payloads of the mod: the per-player dream flag, the graffiti coordinate list and the
 * dream patch set of a chunk.
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

    /** Full list of graffiti coordinates the client needs to render tunnels. */
    public record GraffitiListPayload(List<BlockPos> positions) implements CustomPacketPayload {
        public static final Type<GraffitiListPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(DreamWorldNeoForge121.MOD_ID, "graffiti_list"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GraffitiListPayload> STREAM_CODEC =
                StreamCodec.composite(BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()),
                        GraffitiListPayload::positions, GraffitiListPayload::new);

        @Override
        public Type<GraffitiListPayload> type() {
            return TYPE;
        }
    }

    /**
     * One patched position: the block state the dream layer shows there, plus the real state the
     * overlay hides. The real state comes from the server because the client cannot observe it: by
     * the time a patch arrives, the client position already holds the dream block (or a local
     * prediction of a break), so anything it cached there would be a guess.
     */
    public record PatchEntry(long pos, CompoundTag state, CompoundTag realState) {
        public static final StreamCodec<RegistryFriendlyByteBuf, PatchEntry> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_LONG, PatchEntry::pos,
                        ByteBufCodecs.COMPOUND_TAG, PatchEntry::state,
                        ByteBufCodecs.COMPOUND_TAG, PatchEntry::realState, PatchEntry::new);
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

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(DreamStatePayload.TYPE, DreamStatePayload.STREAM_CODEC,
                new DirectionalPayloadHandler<>(DWClientPayloadHandler::handleDreamState,
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
}
