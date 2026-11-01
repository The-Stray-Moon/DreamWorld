package cc.sighs.dreamworld.dream;

import cc.sighs.dreamworld.DWRegistry;
import cc.sighs.dreamworld.DreamWorldNeoForge121;
import cc.sighs.dreamworld.config.DreamServerConfig;
import cc.sighs.dreamworld.entity.DreamClayEntity;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Sleeping behaviour of the dream world.
 *
 * <p>Players can sleep on the bare ground by sneaking and right-clicking a block at night. This
 * path calls {@link ServerPlayer#startSleeping(BlockPos)} instead of {@code startSleepInBed}, so no
 * respawn point is ever set, and the missing bed block is compensated through
 * {@link CanContinueSleepingEvent} so vanilla does not wake the sleeper up on the next tick.</p>
 *
 * <p>A player carrying the {@code dream_walk} effect may use the gesture at any time of the day.
 * Such a player lies still for the configured entry delay first and only then slips into the mirror
 * layer, so the dream never starts on the very tick of the gesture and an interrupted sleep simply
 * cancels it. Sleeping again while dreaming returns to the bound anchor (or the entry point).</p>
 */
@EventBusSubscriber(modid = DreamWorldNeoForge121.MOD_ID)
public final class DreamSleepEvents {
    /**
     * Ticks a dreamer lies still before waking up again. The entry delay is configured separately
     * ({@link DreamServerConfig#dreamEntryDelayTicks()}), but both stay below the vanilla 100 tick
     * deep-sleep threshold.
     */
    private static final int DREAM_EXIT_DELAY = 60;

    /** Ground sleepers on their way into the dream, keyed by player UUID, value = ticks slept. */
    private static final Map<UUID, Integer> PENDING_DREAM_ENTRY = new HashMap<>();

    /** Dreamers on their way back to reality, keyed by player UUID, value = ticks slept. */
    private static final Map<UUID, Integer> PENDING_DREAM_EXIT = new HashMap<>();

    /**
     * Set while this class wakes a player itself. The resulting {@link PlayerWakeUpEvent} must not
     * be treated as the end of a dream (dream entry) or as a normal wake-up (dream exit).
     */
    private static boolean internalWake;

    private DreamSleepEvents() {
    }

    /**
     * Ticks this sleeper still needs before the dream starts or ends.
     *
     * <p>Dream graffiti draw the sleeper in: inside the configured range the delay shrinks in
     * proportion to the distance to the nearest graffiti, and standing right at one removes the
     * configured share of it. Both directions use this - leaving the dream is a sleep of its own,
     * just with the mirror on the other side. The delay never reaches zero: even on the graffiti the
     * player has to lie still for a moment.</p>
     *
     * @param fallback the delay on that direction used when no graffiti is near
     */
    private static int sleepDelay(ServerPlayer player, int fallback) {
        if (!(player.level() instanceof ServerLevel level)) {
            return fallback;
        }
        double range = DreamServerConfig.graffitiSleepRange();
        if (range <= 0.0D) {
            return fallback;
        }
        double nearest = GraffitiRegistry.get(level).nearestDistance(level, player.blockPosition());
        if (nearest >= range) {
            return fallback;
        }
        double closeness = 1.0D - nearest / range;
        double factor = 1.0D - DreamServerConfig.maxGraffitiSleepSpeedup() * closeness;
        return Math.max(1, (int) Math.round(fallback * factor));
    }

    // ------------------------------------------------------- falling asleep

    /**
     * Sneak + right-click is the "sleep on the ground" gesture. For a dreamer the same gesture is
     * how the dream is left.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // Both hands fire this event; a single gesture must only be handled once.
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!player.isShiftKeyDown()) {
            return;
        }
        // An empty main hand is required: otherwise the same gesture would swallow graffiti
        // placement on the floor (graffiti can only be placed while sneaking on a block face).
        if (!player.getMainHandItem().isEmpty()) {
            return;
        }
        // The gesture is aimed at the ground, so only the top face of a block counts. This keeps
        // sneak-placing graffiti on a wall or a ceiling from ending the dream.
        if (event.getFace() != Direction.UP) {
            return;
        }

        if (DreamManager.isDreaming(player)) {
            // Leaving is a sleep as well: lie down first, and only wake up in reality after the
            // same delay as entering. Waking up in between cancels the exit.
            if (!player.isSleeping() && !player.isPassenger() && !player.isSpectator() && player.isAlive()) {
                player.startSleeping(player.blockPosition());
                level.updateSleepingPlayerList();
                PENDING_DREAM_EXIT.put(player.getUUID(), 0);
            }
            cancelUse(event);
            return;
        }
        if (!canSleepOnGround(player, level)) {
            return;
        }
        startGroundSleep(player, level, player.blockPosition());
        cancelUse(event);
    }

    /**
     * Dream walkers may lie down on any block at any time of the day; everybody else keeps the
     * vanilla rule of needing the night.
     */
    private static boolean canSleepOnGround(ServerPlayer player, ServerLevel level) {
        boolean dreamWalk = player.hasEffect(DWRegistry.DREAM_WALK);
        return (dreamWalk || level.isNight())
                && player.onGround()
                && !player.isPassenger()
                && !player.isSleeping()
                && !player.isSpectator()
                && player.isAlive();
    }

    private static void startGroundSleep(ServerPlayer player, ServerLevel level, BlockPos pos) {
        player.startSleeping(pos);
        level.updateSleepingPlayerList();
        if (!player.hasEffect(DWRegistry.DREAM_WALK)) {
            return;
        }
        // From here the sleep is only the doorway: the body stays in bed until the delay ran out,
        // and the dream is dropped again if the sleeper is woken up in the meantime.
        PENDING_DREAM_ENTRY.put(player.getUUID(), 0);
    }

    /**
     * Counts the ticks every ground sleeper waits for the dream and starts it once the delay is
     * over. Waking up, losing the effect, a dream entered some other way or a disconnect drops the
     * pending entry instead of entering the dream.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!PENDING_DREAM_ENTRY.isEmpty()) {
            Iterator<Map.Entry<UUID, Integer>> iterator = PENDING_DREAM_ENTRY.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, Integer> entry = iterator.next();
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player == null
                        || DreamManager.isDreaming(player)
                        || !player.isSleeping()
                        || !player.hasEffect(DWRegistry.DREAM_WALK)) {
                    iterator.remove();
                    continue;
                }
                int slept = entry.getValue() + 1;
                if (slept < sleepDelay(player, DreamServerConfig.dreamEntryDelayTicks())) {
                    entry.setValue(slept);
                    continue;
                }
                iterator.remove();
                enterDreamFromGround(player);
            }
        }
        if (!PENDING_DREAM_EXIT.isEmpty()) {
            Iterator<Map.Entry<UUID, Integer>> iterator = PENDING_DREAM_EXIT.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, Integer> entry = iterator.next();
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player == null || !DreamManager.isDreaming(player) || !player.isSleeping()) {
                    iterator.remove();
                    continue;
                }
                int slept = entry.getValue() + 1;
                if (slept < sleepDelay(player, DREAM_EXIT_DELAY)) {
                    entry.setValue(slept);
                    continue;
                }
                iterator.remove();
                leaveDream(player);
            }
        }
    }

    /**
     * Leaves the bed after the delay, so the body is free to move while the client shows the mirror
     * layer. Other players keep seeing the dreamer lying down, but that comes from the client-side
     * pose and not from the server's sleep state. The wake-up is internal, so it must not be
     * mistaken for a dream exit.
     */
    private static void enterDreamFromGround(ServerPlayer player) {
        wakeSilently(player);
        DreamManager.enterDream(player);
    }

    /**
     * Keeps a sleeper without a bed asleep: vanilla reports {@code NOT_POSSIBLE_HERE} (no bed) and
     * {@code NOT_POSSIBLE_NOW} (daytime) every tick, both of which would wake the player up.
     */
    @SubscribeEvent
    public static void onContinueSleeping(CanContinueSleepingEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.mayContinueSleeping()) {
            return;
        }
        BlockPos pos = player.getSleepingPos().orElse(null);
        if (pos == null) {
            return;
        }
        // Real beds keep their vanilla rules, only the ground sleep is rescued.
        if (player.level().getBlockState(pos).isBed(player.level(), pos, player)) {
            return;
        }
        event.setContinueSleeping(true);
    }

    // ------------------------------------------------------------ waking up

    @SubscribeEvent
    public static void onWakeUp(PlayerWakeUpEvent event) {
        if (internalWake) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // A ground sleep that is interrupted before the delay ran out never becomes a dream, and it
        // does not leave dream clay behind either.
        if (PENDING_DREAM_ENTRY.remove(player.getUUID()) != null) {
            return;
        }
        // Woken up before the exit sleep was deep enough: stay in the dream.
        if (PENDING_DREAM_EXIT.remove(player.getUUID()) != null) {
            return;
        }

        Optional<BlockPos> sleptAt = player.getSleepingPos();
        if (DreamManager.isDreaming(player)) {
            // Any wake-up that is not caused by the mod itself ends the dream. The teleport is
            // deferred because teleporting here would re-enter stopSleepInBed while it runs.
            var server = player.getServer();
            if (server != null) {
                server.execute(() -> {
                    DreamManager.wakeUp(player);
                    spawnDreamClay(player);
                });
            }
            return;
        }

        // A normal wake-up leaves a blob of dream clay floating next to the bed.
        if (sleptAt.isPresent() && player.level() instanceof ServerLevel level) {
            DreamClayEntity.spawnNear(level, Vec3.atBottomCenterOf(sleptAt.get()));
        }
    }

    // --------------------------------------------------------- dream leaving

    /** Ends the dream, stops any sleep in progress and returns the player to their anchor. */
    private static void leaveDream(ServerPlayer player) {
        if (!DreamManager.isDreaming(player)) {
            return;
        }
        PENDING_DREAM_EXIT.remove(player.getUUID());
        if (player.isSleeping()) {
            wakeSilently(player);
        }
        DreamManager.wakeUp(player);
        // The dream-walk effect is not consumed: it keeps running until its own duration ends.
        // Waking up from the dream leaves dream clay behind, exactly like a normal night's sleep.
        spawnDreamClay(player);
    }

    /** Drops dream clay at the dreamer's wake-up position in the real world. */
    private static void spawnDreamClay(ServerPlayer player) {
        if (player.level() instanceof ServerLevel level) {
            DreamClayEntity.spawnNear(level, Vec3.atBottomCenterOf(player.blockPosition()));
        }
    }

    private static void wakeSilently(ServerPlayer player) {
        boolean previous = internalWake;
        internalWake = true;
        try {
            player.stopSleepInBed(true, true);
        } finally {
            internalWake = previous;
        }
    }

    private static void cancelUse(PlayerInteractEvent.RightClickBlock event) {
        event.setCanceled(true);
        // SUCCESS keeps the client from trying the other hand or the held item afterwards.
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    // ----------------------------------------------------- session boundaries

    /**
     * Re-syncs everything a fresh client session has to know about a returning player.
     *
     * <p>The dream flag lives in persistent data, so a reload while dreaming has to restore the
     * patched view; the anchor flag and the set of dreaming bodies are session state that every
     * client starts without.</p>
     */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        DreamManager.pushAnchorState(player);
        // Always, dreaming or not: the client keeps the body frozen until it is told which layer it
        // is on, so a non-dreamer has to be told "awake" as well or it would stay stuck.
        DreamManager.pushDreamState(player);
        if (player.getServer() != null) {
            // Keep every client's list of dreaming bodies in step with the session.
            DreamManager.broadcastDreamers(player.getServer());
        }
        if (DreamManager.isDreaming(player)) {
            DreamManager.setDreaming(player, true);
            // Chunks are still being sent, and every chunk the player starts watching gets its
            // patch set from the watch event. Re-sending the whole view distance here would only
            // pile a few hundred extra payloads onto the join.
            DreamManager.settleOutOfTerrain(player);
        }
    }

    /** A leaving player must disappear from the other clients' list of dreaming bodies. */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            DreamManager.broadcastDreamers(player.getServer());
        }
    }

    /**
     * A dream never survives death (the flag lives in the old player's persistent data), so the new
     * player is told to drop the dream state no matter what.
     */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DreamManager.setDreaming(player, false);
        }
    }
}
