package cc.sighs.dreamworld.dream;

import cc.sighs.dreamworld.DWObjects;
import cc.sighs.dreamworld.entity.BlubEntity;
import cc.sighs.dreamworld.spi.DreamPlatform;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Server-side lifecycle of the dream layer's creatures.
 *
 * <p>The dream layer has no wildlife: only the blue blub exists there, spawned around dreaming
 * players and dropped again once nobody dreams. Intruded creatures also live here, and this class is
 * what puts them back to sleep after a reload, since the sleeping pose is not part of the save data.</p>
 */
public final class DreamMobs {
    private static final int SPAWN_INTERVAL_TICKS = 200;
    private static final int CLEANUP_INTERVAL_TICKS = 20;
    private static final int BLUBS_NEARBY = 4;
    /** Blubs farther than this from every dreaming player are removed. */
    private static final int BLUB_DESPAWN_RADIUS = 48;
    private static final int BLUB_RADIUS = 32;
    /** Horizontal radius a new blub may appear in, around the dreamer. */
    private static final int BLUB_SPAWN_RADIUS = 16;
    /** Vertical band searched around the dreamer's own height before falling back to the surface. */
    private static final int SPAWN_Y_BAND = 8;

    private DreamMobs() {
    }

    /**
     * Puts a dream copy back to sleep when it (re)enters the level.
     *
     * <p>Entity poses are not saved, so a reloaded intruded creature would otherwise come back
     * standing. A freshly created copy is already posed; this only matters for loaded chunks.</p>
     */
    public static void onEntityJoin(Entity entity) {
        if (!entity.level().isClientSide()) {
            DreamEntities.restoreSleepingPose(entity);
        }
    }

    public static void tick(MinecraftServer server) {
        int tick = server.getTickCount();
        if (tick % CLEANUP_INTERVAL_TICKS != 0) {
            return;
        }
        // The spawn interval is a multiple of the cleanup interval, so this only lines up on
        // spawn ticks.
        boolean spawnTick = tick % SPAWN_INTERVAL_TICKS == 0;
        for (ServerLevel level : server.getAllLevels()) {
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
                discardBlubs(level);
            } else {
                discardFarFromPlayers(level);
            }
        }
    }

    /**
     * Re-runs the player tracking of the level for a single player.
     *
     * <p>{@link BlubEntity#broadcastToPlayer} keeps blubs out of the tracking set of a
     * non-dreaming player, but the tracking loop only revisits an entity when it or the player
     * moves to another section. A dream change therefore has to re-evaluate the set right away:
     * entering the dream sends the blubs nearby, waking removes them again.</p>
     */
    public static void refreshTracking(ServerPlayer player) {
        DreamPlatform.get().refreshEntityTracking(player);
    }

    private static void topUp(ServerLevel level, ServerPlayer player) {
        int nearby = level.getEntitiesOfClass(BlubEntity.class,
                player.getBoundingBox().inflate(BLUB_RADIUS)).size();
        if (nearby < BLUBS_NEARBY) {
            spawnBlub(level, player);
        }
    }

    private static void spawnBlub(ServerLevel level, ServerPlayer player) {
        BlockPos base = player.blockPosition();
        for (int attempt = 0; attempt < 16; attempt++) {
            int dx = level.random.nextInt(BLUB_SPAWN_RADIUS * 2 + 1) - BLUB_SPAWN_RADIUS;
            int dz = level.random.nextInt(BLUB_SPAWN_RADIUS * 2 + 1) - BLUB_SPAWN_RADIUS;
            BlockPos pos = findSpawnSpot(level, base.offset(dx, 0, dz));
            if (pos == null) {
                continue;
            }
            BlubEntity blub = DWObjects.BLUB.get().create(level);
            if (blub == null) {
                return;
            }
            blub.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                    level.random.nextFloat() * 360.0F, 0.0F);
            DreamEntities.setDreamOnly(blub, true);
            if (level.addFreshEntity(blub)) {
                DreamEntities.syncAll(level);
                return;
            }
            blub.discard();
        }
    }

    /**
     * Finds a place the blub can stand on, anywhere except in a fluid.
     *
     * <p>Every biome and every terrain qualifies: the band around the dreamer's own height comes
     * first, because the dream layer may hold terrain that the real world's heightmap knows nothing
     * about (caves, platforms, the whole mirrored surface). Only when that band is blocked does the
     * search fall back to walking down the column from the surface.</p>
     */
    @Nullable
    private static BlockPos findSpawnSpot(ServerLevel level, BlockPos near) {
        for (int dy = SPAWN_Y_BAND; dy >= -SPAWN_Y_BAND; dy--) {
            BlockPos candidate = near.offset(0, dy, 0);
            if (isSpawnable(level, candidate)) {
                return candidate;
            }
        }
        int surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, near).getY();
        for (int y = surface; y >= level.getMinBuildHeight(); y--) {
            BlockPos candidate = new BlockPos(near.getX(), y, near.getZ());
            if (isSpawnable(level, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /** Two air blocks above a sturdy floor, and never inside water or lava. */
    private static boolean isSpawnable(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        if (!level.getFluidState(pos).isEmpty()) {
            return false;
        }
        if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) {
            return false;
        }
        BlockPos below = pos.below();
        BlockState floor = level.getBlockState(below);
        return floor.isFaceSturdy(level, below, Direction.UP);
    }

    /** Removes the blubs of the level right away when nobody dreams in it any more. */
    public static void discardIfNoDreamers(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (DreamManager.isDreaming(player)) {
                return;
            }
        }
        discardBlubs(level);
    }

    /**
     * Removes blubs no dreaming player is near.
     *
     * <p>Only dreamers keep a blub alive: an awake player standing next to one is looking at
     * something that is not part of their world, so their presence must not hold it in place.</p>
     */
    private static void discardFarFromPlayers(ServerLevel level) {
        List<BlubEntity> stray = new ArrayList<>();
        double radiusSqr = (double) BLUB_DESPAWN_RADIUS * BLUB_DESPAWN_RADIUS;
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof BlubEntity blub)) {
                continue;
            }
            if (!hasDreamerNear(level, blub, radiusSqr)) {
                stray.add(blub);
            }
        }
        stray.forEach(Entity::discard);
    }

    private static boolean hasDreamerNear(ServerLevel level, Entity blub, double radiusSqr) {
        for (ServerPlayer player : level.players()) {
            if (DreamManager.isDreaming(player) && player.distanceToSqr(blub) <= radiusSqr) {
                return true;
            }
        }
        return false;
    }

    /** Drops every dream blub of the level; the list is copied so removal cannot break iteration. */
    private static void discardBlubs(ServerLevel level) {
        List<BlubEntity> blubs = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof BlubEntity blub) {
                blubs.add(blub);
            }
        }
        for (BlubEntity blub : blubs) {
            // The blub leaves the dream world: it drops dream clay where it disappeared.
            DreamEntities.leaveDream(blub);
            blub.discard();
        }
    }
}
