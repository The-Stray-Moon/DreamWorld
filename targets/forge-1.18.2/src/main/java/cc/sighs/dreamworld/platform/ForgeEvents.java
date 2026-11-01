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
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ChunkWatchEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-side Forge event adapter; it only translates events into shared calls. */
@Mod.EventBusSubscriber(modid = DreamWorld.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ForgeEvents {
    private ForgeEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        net.minecraft.server.MinecraftServer server =
                net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        DreamSleepEvents.tick(server);
        DreamMobs.tick(server);
        DreamPatchSync.onServerTick(server);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getWorld() instanceof ServerLevel level)) {
            return;
        }
        boolean handled = DreamSleepEvents.onRightClickBlock(player, level, event.getPos(),
                event.getFace(), event.getHand(), player.isShiftKeyDown(),
                player.getMainHandItem().isEmpty());
        if (handled) {
            event.setCanceled(true);
            event.setUseBlock(Event.Result.DENY);
            event.setUseItem(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void onWakeUp(PlayerWakeUpEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            DreamSleepEvents.onWakeUp(player);
        }
    }

    /**
     * Keeps a ground sleeper asleep without a bed.
     *
     * <p>1.18.2 has no {@code CanContinueSleepingEvent}; the vanilla check is
     * {@code ForgeEventFactory#fireSleepingTimeCheck}, which fires {@link SleepingTimeCheckEvent}.
     * Allowing it for a sleeping position that holds no bed replaces the "continue sleeping" hook
     * of later versions.</p>
     */
    @SubscribeEvent
    public static void onSleepingTimeCheck(
            net.minecraftforge.event.entity.player.SleepingTimeCheckEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (!DreamSleepEvents.canContinueSleeping(player, false)) {
            return;
        }
        event.setResult(Event.Result.ALLOW);
    }

    @SubscribeEvent
    public static void onSleepingLocationCheck(
            net.minecraftforge.event.entity.player.SleepingLocationCheckEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.world.entity.player.Player player)) {
            return;
        }
        // LivingEntity#tick runs this check every tick; without ALLOW a bed-less sleeper is
        // woken immediately even though SleepingTimeCheckEvent kept them asleep.
        if (!player.level.getBlockState(event.getSleepingLocation())
                .is(net.minecraft.tags.BlockTags.BEDS)) {
            event.setResult(Event.Result.ALLOW);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            DreamSleepEvents.onLogin(player);
            GraffitiSync.onPlayerLoggedIn(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            DreamSleepEvents.onLogout(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            DreamSleepEvents.onRespawn(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            GraffitiSync.onPlayerChangedDimension(player);
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinWorldEvent event) {
        DreamMobs.onEntityJoin(event.getEntity());
    }

    @SubscribeEvent
    public static void onChunkWatch(ChunkWatchEvent.Watch event) {
        DreamPatchSync.onChunkWatch(event.getPlayer(), event.getWorld(), event.getPos());
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        DreamCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player
                && event.getWorld() instanceof ServerLevel level
                && DreamToolEvents.onBlockBreak(player, level, event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onToolDamage(LivingHurtEvent event) {
        if (DreamToolEvents.onIncomingDamage(event.getEntityLiving(), event.getSource(),
                event.getAmount())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDreamDamage(LivingHurtEvent event) {
        if (DreamDamageRules.shouldCancel(event.getEntityLiving())) {
            event.setCanceled(true);
        }
    }

    /** Mod-bus listener, attached explicitly from the entry point. */
    public static void onEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(DWObjects.BLUB.get(), BlubEntity.dreamAttributes());
    }
}
