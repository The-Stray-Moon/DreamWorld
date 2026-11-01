package cc.sighs.dreamworld.platform;

import cc.sighs.dreamworld.DWObjects;
import cc.sighs.dreamworld.DreamWorld;
import cc.sighs.dreamworld.client.DreamBlink;
import cc.sighs.dreamworld.client.DreamBlockTintSource;
import cc.sighs.dreamworld.client.DreamBoatControls;
import cc.sighs.dreamworld.client.DreamBoatTrail;
import cc.sighs.dreamworld.client.DreamChunkEvents;
import cc.sighs.dreamworld.client.DreamClientState;
import cc.sighs.dreamworld.client.DreamEntityVisibility;
import cc.sighs.dreamworld.client.DreamHud;
import cc.sighs.dreamworld.client.DreamTooltips;
import cc.sighs.dreamworld.client.DreamVisuals;
import cc.sighs.dreamworld.client.DreamWeather;
import cc.sighs.dreamworld.client.entity.BlubModel;
import cc.sighs.dreamworld.client.entity.BlubRenderer;
import cc.sighs.dreamworld.client.entity.DreamSpearRenderer;
import cc.sighs.dreamworld.client.particle.DreamPetalParticle;
import cc.sighs.dreamworld.dream.DreamWorldRules;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

/** Client-side NeoForge event adapter; it only translates events into shared calls. */
@EventBusSubscriber(modid = DreamWorld.MOD_ID, value = Dist.CLIENT)
public final class NeoForgeClientEvents {
    /** Marks a render state whose living entity the local dreamer must not see. */
    private static final ContextKey<Boolean> HIDE_LIVING = new ContextKey<>(
            Identifier.fromNamespaceAndPath(DreamWorld.MOD_ID, "hide_living"));

    private NeoForgeClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        DreamChunkEvents.onClientTickPre();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        DreamChunkEvents.onClientTick();
        DreamBlink.tick();
        DreamWeather.tick();
        DreamBoatTrail.tick();
    }

    /** The graffiti tunnels, submitted as custom geometry of the level pass. */
    @SubscribeEvent
    public static void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
        DreamVisuals.render(event.getLevelRenderState(), event.getPoseStack(),
                event.getSubmitNodeCollector());
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        DreamHud.render(event.getGuiGraphics());
        DreamBlink.renderGui(event.getGuiGraphics());
    }

    @SubscribeEvent
    public static void onMouse(InputEvent.MouseButton.Pre event) {
        DreamBoatControls.onMouseButton(event.getButton(), event.getAction());
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        DreamBoatControls.onKey(event.getKeyEvent(), event.getAction());
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        DreamChunkEvents.onLoggingOut();
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        DreamChunkEvents.onLoggingIn();
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        DreamChunkEvents.onChunkLoad(event.getLevel().isClientSide(),
                net.minecraft.world.level.ChunkPos.pack(event.getChunk().getPos().x(), event.getChunk().getPos().z()));
    }

    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?, ?> event) {
        if (Boolean.TRUE.equals(event.getRenderState().getRenderData(HIDE_LIVING))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        DreamTooltips.append(event.getItemStack(), event.getToolTip());
    }

    @SubscribeEvent
    public static void registerRenderStateModifiers(RegisterRenderStateModifiersEvent event) {
        registerLivingVisibility(event);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerLivingVisibility(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier((Class) net.minecraft.client.renderer.entity.LivingEntityRenderer.class,
                (entity, state) -> {
                    if (entity instanceof LivingEntity living
                            && DreamEntityVisibility.shouldHide(living)) {
                        state.setRenderData(HIDE_LIVING, Boolean.TRUE);
                    }
                });
    }

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(DWObjects.DREAM_PETAL.get(), DreamPetalParticle.Provider::new);
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DWObjects.DREAM_CLAY_ENTITY.get(), ThrownItemRenderer::new);
        event.registerEntityRenderer(DWObjects.DREAM_SPEAR_ENTITY.get(), DreamSpearRenderer::new);
        event.registerEntityRenderer(DWObjects.BLUB.get(), BlubRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(BlubModel.LAYER, BlubModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerRenderPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(DreamVisuals.TUNNEL_PIPELINE);
    }

    /**
     * Registers the dream palette for every block, wrapping whatever tint source the block already
     * had so grass, leaves and water keep their own colour as the base of the rotation.
     */
    @SubscribeEvent
    public static void registerBlockTints(RegisterColorHandlersEvent.BlockTintSources event) {
        var colors = event.getBlockColors();
        for (Block block : BuiltInRegistries.BLOCK) {
            var original = colors.getTintSources(block.defaultBlockState());
            if (original.isEmpty()) {
                event.register(java.util.List.of(new DreamBlockTintSource(
                        net.minecraft.client.color.block.BlockTintSources.constant(-1),
                        DreamBlockTintSource.isWater(block.defaultBlockState()))), block);
                continue;
            }
            java.util.List<BlockTintSource> wrapped = original.stream()
                    .map(source -> (BlockTintSource) new DreamBlockTintSource(source,
                            DreamBlockTintSource.isWater(block.defaultBlockState())))
                    .toList();
            event.register(wrapped, block);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (DreamWorldRules.isDreaming(event.getEntity())
                && !DreamWorldRules.mayBreak(event.getEntity(),
                        event.getLevel().getBlockState(event.getPos()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (DreamWorldRules.isDreaming(event.getEntity())
                && !DreamWorldRules.mayInteractBlock(event.getEntity(),
                        event.getLevel().getBlockState(event.getPos()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (DreamWorldRules.isDreaming(event.getEntity())
                && !DreamWorldRules.mayUseItem(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (DreamWorldRules.isDreaming(event.getEntity())
                && !DreamWorldRules.mayInteractEntity(event.getEntity(), event.getTarget())) {
            event.setCanceled(true);
        }
    }

}
