package cc.sighs.dreamworld.platform;

import cc.sighs.dreamworld.DWObjects;
import cc.sighs.dreamworld.DreamWorld;
import cc.sighs.dreamworld.client.DreamBlink;
import cc.sighs.dreamworld.client.DreamBoatControls;
import cc.sighs.dreamworld.client.DreamBoatTrail;
import cc.sighs.dreamworld.client.DreamChunkEvents;
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
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

/** Client-side NeoForge event adapter; it only translates events into shared calls. */
@EventBusSubscriber(modid = DreamWorld.MOD_ID, value = Dist.CLIENT)
public final class NeoForgeClientEvents {
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

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            DreamBlink.renderLevel(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            DreamVisuals.render(event.getCamera());
        }
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
        DreamBoatControls.onKey(event.getKey(), event.getScanCode(), event.getAction());
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
                event.getChunk().getPos().toLong());
    }

    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity entity = event.getEntity();
        if (DreamEntityVisibility.shouldHide(entity)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        DreamTooltips.append(event.getItemStack(), event.getToolTip());
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
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(DreamBlink.reloadListener());
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
