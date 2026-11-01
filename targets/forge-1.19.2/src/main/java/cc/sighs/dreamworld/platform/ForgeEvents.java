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
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.event.entity.player.SleepingTimeCheckEvent;
import net.minecraftforge.event.GrindstoneEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkWatchEvent;
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

    /**
     * 1.19.2 has no CanContinueSleepingEvent. The sleeping-time check fires every tick for a
     * sleeping player, and ALLOW is what keeps a ground sleeper without a bed asleep.
     */
    @SubscribeEvent
    public static void onSleepingTimeCheck(SleepingTimeCheckEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && DreamSleepEvents.canContinueSleeping(player, false)) {
            event.setResult(Event.Result.ALLOW);
        }
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
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player
                && event.getLevel() instanceof ServerLevel level
                && DreamToolEvents.onBlockBreak(player, level, event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onToolDamage(LivingDamageEvent event) {
        if (DreamToolEvents.onIncomingDamage(event.getEntity(), event.getSource(),
                event.getAmount())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDreamDamage(LivingDamageEvent event) {
        if (DreamDamageRules.shouldCancel(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** Grinding a lone dream crystal back into dream powder. */
    @SubscribeEvent
    public static void onGrindstonePlace(GrindstoneEvent.OnplaceItem event) {
        if (event.getTopItem().is(DWObjects.DREAM_CRYSTAL.get()) && event.getBottomItem().isEmpty()) {
            event.setOutput(new ItemStack(DWObjects.DREAM_POWDER.get()));
            event.setXp(1);
        }
    }
}
