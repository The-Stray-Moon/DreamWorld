package cc.sighs.dreamworld.dream;

import cc.sighs.dreamworld.DWRegistry;
import cc.sighs.dreamworld.DreamWorldNeoForge121;
import cc.sighs.dreamworld.entity.DreamRabbitEntity;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Server-side lifecycle of the dream layer's creatures.
 *
 * <p>The dream layer has no wildlife: only the blue rabbit exists there, spawned around dreaming
 * players and dropped again once nobody dreams. Intruded creatures also live here, and this class is
 * what puts them back to sleep after a reload, since the sleeping pose is not part of the save data.</p>
 */
@EventBusSubscriber(modid = DreamWorldNeoForge121.MOD_ID)
public final class DreamMobs {
    private static final int SPAWN_INTERVAL_TICKS = 200;
    private static final int CLEANUP_INTERVAL_TICKS = 20;
    private static final int RABBITS_NEARBY = 4;
    /** Rabbits farther than this from every player are removed; they only exist near a dreamer. */
    private static final int RABBIT_DESPAWN_RADIUS = 48;
    private static final int RABBIT_RADIUS = 32;

    private DreamMobs() {
    }

    /**
     * Puts a dream copy back to sleep when it (re)enters the level.
     *
     * <p>Entity poses are not saved, so a reloaded intruded creature would otherwise come back
     * standing. A freshly created copy is already posed; this only matters for loaded chunks.</p>
     */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            DreamEntities.restoreSleepingPose(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        int tick = event.getServer().getTickCount();
        if (tick % CLEANUP_INTERVAL_TICKS != 0) {
            return;
        }
        // The spawn interval is a multiple of the cleanup interval, so this only lines up on
        // spawn ticks.
        boolean spawnTick = tick % SPAWN_INTERVAL_TICKS == 0;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            boolean hasDreamer = false;
            for (ServerPlayer player : level.players()) {
                if (!DreamManager.isDreaming(player)) {
                    continue;
                }
                hasDreamer = true;
                if (spawnTick) {
                    topUp(level, player);
                }
                // Keep the client visibility list in step with spawns, deaths and cleanups.
                DreamEntities.sync(player);
                DreamEntities.refreshVisibility(player);
            }
            if (!hasDreamer) {
                discardRabbits(level);
            } else {
                discardFarFromPlayers(level);
            }
        }
    }

    /**
     * Re-runs the player tracking of the level for a single player.
     *
     * <p>{@link DreamRabbitEntity#broadcastToPlayer} keeps rabbits out of the tracking set of a
     * non-dreaming player, but the tracking loop only revisits an entity when it or the player
     * moves to another section. A dream change therefore has to re-evaluate the set right away:
     * entering the dream sends the rabbits nearby, waking removes them again.</p>
     */
    public static void refreshTracking(ServerPlayer player) {
        var server = player.getServer();
        if (server == null || player.isChangingDimension()) {
            return;
        }
        // Deferred to the next tick: right after a teleport/dimension change the chunk map's
        // tracking sets are being rebuilt and ChunkMap#move would dereference a null set.
        server.execute(() -> {
            if (player.isRemoved() || player.hasDisconnected()) {
                return;
            }
            if (player.level() instanceof ServerLevel level) {
                try {
                    level.getChunkSource().chunkMap.move(player);
                } catch (RuntimeException ignored) {
                    // Tracking is also refreshed by the next natural move; never fail the dream entry.
                }
            }
        });
    }

    private static void topUp(ServerLevel level, ServerPlayer player) {
        int nearby = level.getEntitiesOfClass(DreamRabbitEntity.class,
                player.getBoundingBox().inflate(RABBIT_RADIUS)).size();
        if (nearby < RABBITS_NEARBY) {
            spawnRabbit(level, player);
        }
    }

    private static void spawnRabbit(ServerLevel level, ServerPlayer player) {
        BlockPos base = player.blockPosition();
        for (int attempt = 0; attempt < 12; attempt++) {
            int dx = level.random.nextInt(17) - 8;
            int dz = level.random.nextInt(17) - 8;
            BlockPos pos = safeSpawnPos(level, base.offset(dx, 0, dz));
            if (pos == null) {
                continue;
            }
            DreamRabbitEntity rabbit = DWRegistry.DREAM_RABBIT.get().create(level);
            if (rabbit == null) {
                return;
            }
            rabbit.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                    level.random.nextFloat() * 360.0F, 0.0F);
            DreamEntities.setDreamOnly(rabbit, true);
            if (level.addFreshEntity(rabbit)) {
                DreamEntities.syncAll(level);
                return;
            }
            rabbit.discard();
        }
    }

    /**
     * A spot where the rabbit can stand: two air blocks above a solid floor. The surface heightmap
     * alone is not enough, it happily points inside a hill or a tree and the rabbit would suffocate
     * before anyone sees it.
     */
    @Nullable
    private static BlockPos safeSpawnPos(ServerLevel level, BlockPos column) {
        // Prefer the dreamer's own height first: the rabbits are meant to appear around the
        // dreamer, and the column below may be a tree canopy or an unloaded heightmap (-64).
        if (level.getBlockState(column).isAir() && level.getBlockState(column.above()).isAir()) {
            return column;
        }
        int surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).getY();
        for (int dy = 0; dy >= -24; dy--) {
            BlockPos candidate = new BlockPos(column.getX(), surface + dy, column.getZ());
            if (level.getBlockState(candidate).isAir()
                    && level.getBlockState(candidate.above()).isAir()
                    && level.getBlockState(candidate.below()).isFaceSturdy(level, candidate.below(),
                            net.minecraft.core.Direction.UP)) {
                return candidate;
            }
        }
        return null;
    }

    /** Removes the rabbits of the level right away when nobody dreams in it any more. */
    public static void discardIfNoDreamers(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (DreamManager.isDreaming(player)) {
                return;
            }
        }
        discardRabbits(level);
    }

    /** Removes rabbits that no player is near; a rabbit only exists around a dreamer. */
    private static void discardFarFromPlayers(ServerLevel level) {
        List<DreamRabbitEntity> stray = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof DreamRabbitEntity rabbit)) {
                continue;
            }
            boolean near = level.getNearestPlayer(rabbit, RABBIT_DESPAWN_RADIUS) != null;
            if (!near) {
                stray.add(rabbit);
            }
        }
        stray.forEach(Entity::discard);
    }

    /** Drops every dream rabbit of the level; the list is copied so removal cannot break iteration. */
    private static void discardRabbits(ServerLevel level) {
        List<DreamRabbitEntity> rabbits = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof DreamRabbitEntity rabbit) {
                rabbits.add(rabbit);
            }
        }
        for (DreamRabbitEntity rabbit : rabbits) {
            // The rabbit leaves the dream world: it drops dream clay where it disappeared.
            DreamEntities.leaveDream(rabbit);
            rabbit.discard();
        }
    }
}
