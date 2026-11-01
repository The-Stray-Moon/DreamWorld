package cc.sighs.dreamworld.platform;

import cc.sighs.dreamworld.DWObjects;
import cc.sighs.dreamworld.DreamWorld;
import cc.sighs.dreamworld.client.DWClientPayloadHandler;
import cc.sighs.dreamworld.client.DreamBlink;
import cc.sighs.dreamworld.client.DreamBoatTrail;
import cc.sighs.dreamworld.client.DreamChunkEvents;
import cc.sighs.dreamworld.client.DreamHud;
import cc.sighs.dreamworld.client.DreamTooltips;
import cc.sighs.dreamworld.client.DreamVisuals;
import cc.sighs.dreamworld.client.DreamWeather;
import cc.sighs.dreamworld.client.entity.BlubModel;
import cc.sighs.dreamworld.client.entity.BlubRenderer;
import cc.sighs.dreamworld.client.entity.DreamSpearRenderer;
import cc.sighs.dreamworld.client.particle.DreamPetalParticle;
import cc.sighs.dreamworld.net.DWPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Client-side Fabric event adapter; it only translates events into shared calls. */
public final class FabricClientEvents {
    private FabricClientEvents() {
    }

    public static void register() {
        ClientTickEvents.START_CLIENT_TICK.register(client -> DreamChunkEvents.onClientTickPre());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            DreamChunkEvents.onClientTick();
            DreamBlink.tick();
            DreamWeather.tick();
            DreamBoatTrail.tick();
        });

        WorldRenderEvents.AFTER_TRANSLUCENT.register(context ->
                DreamVisuals.render(context.camera()));
        WorldRenderEvents.LAST.register(context ->
                DreamBlink.renderLevel(
                        context.tickCounter().getGameTimeDeltaPartialTick(false)));
        HudRenderCallback.EVENT.register((graphics, deltaTracker) -> {
            DreamHud.render(graphics);
            DreamBlink.renderGui(graphics);
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                DreamChunkEvents.onLoggingIn());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                DreamChunkEvents.onLoggingOut());

        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) ->
                DreamChunkEvents.onChunkLoad(true, chunk.getPos().toLong()));

        ItemTooltipCallback.EVENT.register((stack, context, type, lines) ->
                DreamTooltips.append(stack, lines));

        ParticleFactoryRegistry.getInstance().register(DWObjects.DREAM_PETAL.get(),
                DreamPetalParticle.Provider::new);
        EntityRendererRegistry.register(DWObjects.DREAM_CLAY_ENTITY.get(),
                ThrownItemRenderer::new);
        EntityRendererRegistry.register(DWObjects.DREAM_SPEAR_ENTITY.get(),
                DreamSpearRenderer::new);
        EntityRendererRegistry.register(DWObjects.BLUB.get(), BlubRenderer::new);
        EntityModelLayerRegistry.registerModelLayer(BlubModel.LAYER, BlubModel::createBodyLayer);

        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                new IdentifiableResourceReloadListener() {
                    private final net.minecraft.server.packs.resources.PreparableReloadListener delegate =
                            DreamBlink.reloadListener();

                    @Override
                    public ResourceLocation getFabricId() {
                        return ResourceLocation.fromNamespaceAndPath(DreamWorld.MOD_ID, "blink");
                    }

                    @Override
                    public CompletableFuture<Void> reload(
                            net.minecraft.server.packs.resources.PreparableReloadListener.PreparationBarrier synchronizer,
                            ResourceManager manager, ProfilerFiller prepareProfiler,
                            ProfilerFiller applyProfiler, Executor backgroundExecutor,
                            Executor gameExecutor) {
                        return delegate.reload(synchronizer, manager, prepareProfiler,
                                applyProfiler, backgroundExecutor, gameExecutor);
                    }
                });

        registerClientReceivers();
    }

    private static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(DWPayloads.DreamStatePayload.TYPE,
                (payload, context) -> context.client().execute(
                        () -> DWClientPayloadHandler.handleDreamState(payload.common())));
        ClientPlayNetworking.registerGlobalReceiver(DWPayloads.DreamersPayload.TYPE,
                (payload, context) -> context.client().execute(
                        () -> DWClientPayloadHandler.handleDreamers(payload.common())));
        ClientPlayNetworking.registerGlobalReceiver(DWPayloads.BlastParticlesPayload.TYPE,
                (payload, context) -> context.client().execute(
                        () -> DWClientPayloadHandler.handleBlastParticles(payload.common())));
        ClientPlayNetworking.registerGlobalReceiver(DWPayloads.PetalShapePayload.TYPE,
                (payload, context) -> context.client().execute(
                        () -> DWClientPayloadHandler.handlePetalShape(payload.common())));
        ClientPlayNetworking.registerGlobalReceiver(DWPayloads.AnchorStatePayload.TYPE,
                (payload, context) -> context.client().execute(
                        () -> DWClientPayloadHandler.handleAnchorState(payload.common())));
        ClientPlayNetworking.registerGlobalReceiver(DWPayloads.GraffitiListPayload.TYPE,
                (payload, context) -> context.client().execute(
                        () -> DWClientPayloadHandler.handleGraffitiList(payload.common())));
        ClientPlayNetworking.registerGlobalReceiver(DWPayloads.DreamEntitiesPayload.TYPE,
                (payload, context) -> context.client().execute(
                        () -> DWClientPayloadHandler.handleDreamEntities(payload.common())));
        ClientPlayNetworking.registerGlobalReceiver(DWPayloads.DreamPatchPayload.TYPE,
                (payload, context) -> context.client().execute(
                        () -> DWClientPayloadHandler.handleDreamPatch(payload.common())));
    }
}
