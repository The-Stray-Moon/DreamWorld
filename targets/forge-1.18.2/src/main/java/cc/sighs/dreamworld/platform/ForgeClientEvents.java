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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ParticleFactoryRegisterEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-side Forge event adapter; it only translates events into shared calls. */
@Mod.EventBusSubscriber(modid = DreamWorld.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ForgeClientEvents {
    private ForgeClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTickPre(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            DreamChunkEvents.onClientTickPre();
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        DreamChunkEvents.onClientTick();
        DreamBlink.tick();
        DreamWeather.tick();
        DreamBoatTrail.tick();
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            DreamVisuals.render(event.getCamera());
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        DreamHud.render(event.getMatrixStack());
        DreamBlink.renderGui(event.getMatrixStack());
    }

    @SubscribeEvent
    public static void onMouse(InputEvent.MouseInputEvent event) {
        DreamBoatControls.onMouseButton(event.getButton(), event.getAction());
    }

    @SubscribeEvent
    public static void onKey(InputEvent.KeyInputEvent event) {
        DreamBoatControls.onKey(event.getKey(), event.getScanCode(), event.getAction());
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggedOutEvent event) {
        DreamChunkEvents.onLoggingOut();
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggedInEvent event) {
        DreamChunkEvents.onLoggingIn();
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        boolean clientSide = event.getWorld() instanceof net.minecraft.world.level.Level level
                && level.isClientSide;
        DreamChunkEvents.onChunkLoad(clientSide, event.getChunk().getPos().toLong());
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
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (DreamWorldRules.isDreaming(event.getPlayer())
                && !DreamWorldRules.mayBreak(event.getPlayer(),
                        event.getWorld().getBlockState(event.getPos()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (DreamWorldRules.isDreaming(event.getPlayer())
                && !DreamWorldRules.mayInteractBlock(event.getPlayer(),
                        event.getWorld().getBlockState(event.getPos()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (DreamWorldRules.isDreaming(event.getPlayer())
                && !DreamWorldRules.mayUseItem(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (DreamWorldRules.isDreaming(event.getPlayer())
                && !DreamWorldRules.mayInteractEntity(event.getPlayer(), event.getTarget())) {
            event.setCanceled(true);
        }
    }

    /** Mod-bus listeners: particle providers, renderers, layer definitions, reload listener. */
    @Mod.EventBusSubscriber(modid = DreamWorld.MOD_ID, value = Dist.CLIENT,
            bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        private ModBus() {
        }

        @SubscribeEvent
        public static void registerParticleProviders(ParticleFactoryRegisterEvent event) {
            Minecraft.getInstance().particleEngine.register(DWObjects.DREAM_PETAL.get(),
                    DreamPetalParticle.Provider::new);
        }

        @SubscribeEvent
        public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(DWObjects.DREAM_CLAY_ENTITY.get(), ThrownItemRenderer::new);
            event.registerEntityRenderer(DWObjects.DREAM_SPEAR_ENTITY.get(), DreamSpearRenderer::new);
            event.registerEntityRenderer(DWObjects.BLUB.get(), BlubRenderer::new);
        }

        @SubscribeEvent
        public static void registerLayerDefinitions(
                EntityRenderersEvent.RegisterLayerDefinitions event) {
            event.registerLayerDefinition(BlubModel.LAYER, BlubModel::createBodyLayer);
        }

        @SubscribeEvent
        public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener(DreamBlink.reloadListener());
        }
    }
}
