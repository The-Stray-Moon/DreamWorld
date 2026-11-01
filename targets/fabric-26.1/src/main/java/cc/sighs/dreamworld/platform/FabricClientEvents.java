package cc.sighs.dreamworld.platform;

import cc.sighs.dreamworld.DWObjects;
import cc.sighs.dreamworld.DreamWorld;
import cc.sighs.dreamworld.client.DWClientPayloadHandler;
import cc.sighs.dreamworld.client.DreamBlink;
import cc.sighs.dreamworld.client.DreamBlockTint;
import cc.sighs.dreamworld.client.DreamBlockTintSource;
import cc.sighs.dreamworld.client.DreamBoatTrail;
import cc.sighs.dreamworld.client.DreamChunkEvents;
import cc.sighs.dreamworld.client.DreamClientState;
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
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Client-side Fabric event adapter; it only translates events into shared calls. */
public final class FabricClientEvents {
    private static final Identifier HUD_ELEMENT = Identifier.fromNamespaceAndPath(
            DreamWorld.MOD_ID, "hints");

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

        // 26.1 draws the level from submitted geometry: the tunnels go in with the custom geometry
        // of the level pass, and the sky is overridden on the extracted render state.
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> DreamVisuals.render(
                context.levelState(), context.poseStack(), context.submitNodeCollector()));
        LevelRenderEvents.END_EXTRACTION.register(FabricClientEvents::overrideSky);

        HudElementRegistry.addLast(HUD_ELEMENT, (graphics, deltaTracker) -> {
            DreamHud.render(graphics);
            DreamBlink.renderGui(graphics);
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                DreamChunkEvents.onLoggingIn());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                DreamChunkEvents.onLoggingOut());

        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) ->
                DreamChunkEvents.onChunkLoad(level.isClientSide(),
                        ChunkPos.pack(chunk.getPos().x(), chunk.getPos().z())));

        ItemTooltipCallback.EVENT.register((stack, context, type, lines) ->
                DreamTooltips.append(stack, lines));

        ParticleProviderRegistry.getInstance().register(DWObjects.DREAM_PETAL.get(),
                DreamPetalParticle.Provider::new);
        EntityRendererRegistry.register(DWObjects.DREAM_CLAY_ENTITY.get(),
                ThrownItemRenderer::new);
        EntityRendererRegistry.register(DWObjects.DREAM_SPEAR_ENTITY.get(),
                DreamSpearRenderer::new);
        EntityRendererRegistry.register(DWObjects.BLUB.get(), BlubRenderer::new);
        ModelLayerRegistry.registerModelLayer(BlubModel.LAYER, BlubModel::createBodyLayer);

        registerBlockTints();
        registerClientReceivers();
    }

    /**
     * The dream palette for every block, wrapping whatever tint source vanilla registered.
     *
     * <p>NeoForge hands the live {@code BlockColors} to its event; Fabric only offers
     * {@link BlockColorRegistry}, which cannot read the sources it is replacing. The vanilla defaults
     * are rebuilt into a fresh {@link BlockColors} instead, so grass, leaves and water keep their own
     * colour as the base of the rotation. Sources added by other mods are not seen and fall back to a
     * constant tint.</p>
     */
    private static void registerBlockTints() {
        BlockColors defaults = BlockColors.createDefault();
        for (Block block : BuiltInRegistries.BLOCK) {
            boolean water = DreamBlockTintSource.isWater(block.defaultBlockState());
            List<BlockTintSource> original = defaults.getTintSources(block.defaultBlockState());
            List<BlockTintSource> wrapped = original.isEmpty()
                    ? List.of(new DreamBlockTintSource(BlockTintSources.constant(-1), water))
                    : original.stream()
                            .map(source -> (BlockTintSource) new DreamBlockTintSource(source, water))
                            .toList();
            BlockColorRegistry.register(wrapped, block);
        }
    }

    /** The dream sky: permanent noon, the palette's sky colour and no stars. */
    private static void overrideSky(
            net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext context) {
        if (!DreamClientState.isDreaming()) {
            return;
        }
        SkyRenderState sky = context.levelState().skyRenderState;
        Vec3 colour = DreamBlockTint.skyColor();
        sky.skyColor = ARGB.colorFromFloat(1.0F, (float) colour.x, (float) colour.y,
                (float) colour.z);
        sky.sunAngle = 0.0F;
        sky.starBrightness = 0.0F;
        sky.rainBrightness = 1.0F;
        sky.shouldRenderDarkDisc = false;
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
