package cc.sighs.dreamworld.platform;

import cc.sighs.dreamworld.DWObjects;
import cc.sighs.dreamworld.DreamWorld;
import cc.sighs.dreamworld.dream.DreamCommand;
import cc.sighs.dreamworld.dream.DreamDamageRules;
import cc.sighs.dreamworld.dream.DreamMobs;
import cc.sighs.dreamworld.dream.DreamPatchSync;
import cc.sighs.dreamworld.dream.DreamSleepEvents;
import cc.sighs.dreamworld.dream.GraffitiSync;
import cc.sighs.dreamworld.entity.BlubEntity;
import cc.sighs.dreamworld.item.DreamToolEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Server-side NeoForge event adapter; it only translates events into shared calls. */
@EventBusSubscriber(modid = DreamWorld.MOD_ID)
public final class NeoForgeEvents {
    private NeoForgeEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        DreamSleepEvents.tick(event.getServer());
        DreamMobs.tick(event.getServer());
        DreamPatchSync.onServerTick(event.getServer());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        boolean handled = DreamSleepEvents.onRightClickBlock(player, level, event.getPos(),
                event.getFace(), event.getHand(), player.isShiftKeyDown(),
                player.getMainHandItem().isEmpty());
        if (handled) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent
    public static void onContinueSleeping(CanContinueSleepingEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && DreamSleepEvents.canContinueSleeping(player, event.mayContinueSleeping())) {
            event.setContinueSleeping(true);
        }
    }

    @SubscribeEvent
    public static void onWakeUp(PlayerWakeUpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DreamSleepEvents.onWakeUp(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DreamSleepEvents.onLogin(player);
            GraffitiSync.onPlayerLoggedIn(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DreamSleepEvents.onLogout(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DreamSleepEvents.onRespawn(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            GraffitiSync.onPlayerChangedDimension(player);
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        DreamMobs.onEntityJoin(event.getEntity());
    }

    @SubscribeEvent
    public static void onChunkWatch(ChunkWatchEvent.Watch event) {
        DreamPatchSync.onChunkWatch(event.getPlayer(), event.getLevel(), event.getPos());
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        DreamCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onBlockBreak(BreakBlockEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player
                && event.getLevel() instanceof ServerLevel level
                && DreamToolEvents.onBlockBreak(player, level, event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onToolDamage(LivingIncomingDamageEvent event) {
        if (DreamToolEvents.onIncomingDamage(event.getEntity(), event.getSource(),
                event.getAmount())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDreamDamage(LivingIncomingDamageEvent event) {
        if (DreamDamageRules.shouldCancel(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(DWObjects.BLUB.get(), BlubEntity.dreamAttributes());
    }
}
