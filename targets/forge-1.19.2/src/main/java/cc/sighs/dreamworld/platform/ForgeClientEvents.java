package cc.sighs.dreamworld.platform;

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
import cc.sighs.dreamworld.dream.DreamWorldRules;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-side Forge event adapter; it only translates events into shared calls. */
@Mod.EventBusSubscriber(modid = DreamWorld.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class ForgeClientEvents {
    private ForgeClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            DreamChunkEvents.onClientTickPre();
            return;
        }
        DreamChunkEvents.onClientTick();
        DreamBlink.tick();
        DreamWeather.tick();
        DreamBoatTrail.tick();
        DreamHud.tick();
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        // 1.19.2 has no AFTER_LEVEL stage; AFTER_WEATHER is the last point the level is still
        // being drawn and the GUI has not started, which is exactly the blink's slot.
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            DreamBlink.renderLevel(event.getPartialTick());
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            DreamVisuals.render(event.getCamera(), event.getPoseStack());
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        DreamBlink.renderGui(event.getPoseStack());
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
