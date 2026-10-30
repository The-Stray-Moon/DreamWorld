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
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Keeps dream rabbits around dreaming players.
 *
 * <p>The dream layer has no wildlife: only the blue rabbit exists there. The rabbits are real
 * server-side entities shared by every dreaming player of a level, and they lay dream clay. Without
 * a single dreamer in a level they are dropped, so nothing keeps wandering through the real world.</p>
 */
@EventBusSubscriber(modid = DreamWorldNeoForge121.MOD_ID)
public final class DreamMobs {
    private static final int SPAWN_INTERVAL_TICKS = 200;
    private static final int CLEANUP_INTERVAL_TICKS = 20;
    private static final int RABBITS_NEARBY = 4;
    private static final int RABBIT_RADIUS = 32;

    private DreamMobs() {
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
            }
            if (!hasDreamer) {
                discardRabbits(level);
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
            if (level.addFreshEntity(rabbit)) {
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

    /** Drops every dream rabbit of the level; the list is copied so removal cannot break iteration. */
    private static void discardRabbits(ServerLevel level) {
        List<DreamRabbitEntity> rabbits = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof DreamRabbitEntity rabbit) {
                rabbits.add(rabbit);
            }
        }
        for (DreamRabbitEntity rabbit : rabbits) {
            rabbit.discard();
        }
    }
}
