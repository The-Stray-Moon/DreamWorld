package cc.sighs.dreamworld.dream;

import cc.sighs.dreamworld.DWRegistry;
import cc.sighs.dreamworld.DreamWorldNeoForge121;
import cc.sighs.dreamworld.entity.DreamClayEntity;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;

/**
 * Sleeping behaviour of the dream world.
 *
 * <p>Players can sleep on the bare ground by sneaking and right-clicking a block at night. This
 * path calls {@link ServerPlayer#startSleeping(BlockPos)} instead of {@code startSleepInBed}, so no
 * respawn point is ever set, and the missing bed block is compensated through
 * {@link CanContinueSleepingEvent} so vanilla does not wake the sleeper up on the next tick.</p>
 *
 * <p>Sleeping with the {@code dream_walk} effect enters the mirror layer; sleeping again while
 * dreaming returns to the bound anchor (or the entry point).</p>
 */
@EventBusSubscriber(modid = DreamWorldNeoForge121.MOD_ID)
public final class DreamSleepEvents {
    /**
     * Set while this class wakes a player itself. The resulting {@link PlayerWakeUpEvent} must not
     * be treated as the end of a dream (dream entry) or as a normal wake-up (dream exit).
     */
    private static boolean internalWake;

    private DreamSleepEvents() {
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
            leaveDream(player);
            cancelUse(event);
            return;
        }
        if (!canSleepOnGround(player, level)) {
            return;
        }
        startGroundSleep(player, level, player.blockPosition());
        cancelUse(event);
    }

    private static boolean canSleepOnGround(ServerPlayer player, ServerLevel level) {
        return level.isNight()
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
        // Sleeping is only the doorway: leave the bed right away and step into the mirror layer.
        // The wake-up is internal, so it must not be mistaken for a dream exit.
        wakeSilently(player);
        DreamManager.enterDream(player);
    }

    /**
     * Keeps a sleeper without a bed asleep: vanilla reports {@code NOT_POSSIBLE_HERE} (no bed) and
     * {@code NOT_POSSIBLE_NOW} (daytime) every tick, both of which would wake the player up.
     */
    /** Mounting or dismounting changes who owns the movement, so the dream body is re-applied. */
    @SubscribeEvent
    public static void onMount(EntityMountEvent event) {
        if (event.getEntityMounting() instanceof ServerPlayer player && DreamManager.isDreaming(player)) {
            DreamManager.refreshDreamMovement(player);
        }
    }

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

        Optional<BlockPos> sleptAt = player.getSleepingPos();
        if (DreamManager.isDreaming(player)) {
            // Any wake-up that is not caused by the mod itself ends the dream. The teleport is
            // deferred because teleporting here would re-enter stopSleepInBed while it runs.
            var server = player.getServer();
            if (server != null) {
                server.execute(() -> {
                    DreamManager.wakeUp(player);
                    player.removeEffect(DWRegistry.DREAM_WALK);
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
        if (player.isSleeping()) {
            wakeSilently(player);
        }
        DreamManager.wakeUp(player);
        player.removeEffect(DWRegistry.DREAM_WALK);
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
     * The dream flag is stored in the player's persistent data, but the forced sleeping pose is
     * not saved. Re-applying it on login also re-syncs the client visuals.
     */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && DreamManager.isDreaming(player)) {
            DreamManager.setDreaming(player, true);
            DreamManager.grantDreamMovement(player);
            DreamPatchSync.sendAround(player);
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
