package cc.sighs.dreamworld.dream;

import cc.sighs.dreamworld.DWRegistry;
import cc.sighs.dreamworld.DreamWorldNeoForge121;
import cc.sighs.dreamworld.entity.DreamRabbitEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Keeps dream rabbits around dreaming players.
 *
 * <p>The dream layer has no wildlife: only the blue rabbit exists there. The entities are real
 * server-side so they can lay dream clay, but clients only render them for players who are
 * dreaming; everybody else never sees them.</p>
 */
@EventBusSubscriber(modid = DreamWorldNeoForge121.MOD_ID)
public final class DreamMobs {
    private static final int SPAWN_INTERVAL_TICKS = 200;
    private static final int RABBITS_NEARBY = 4;
    private static final int RABBIT_RADIUS = 32;

    private DreamMobs() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % SPAWN_INTERVAL_TICKS != 0) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                if (!DreamManager.isDreaming(player)) {
                    continue;
                }
                int nearby = level.getEntitiesOfClass(DreamRabbitEntity.class,
                        player.getBoundingBox().inflate(RABBIT_RADIUS)).size();
                if (nearby < RABBITS_NEARBY) {
                    spawnRabbit(level, player);
                }
            }
        }
    }

    private static void spawnRabbit(ServerLevel level, ServerPlayer player) {
        BlockPos base = player.blockPosition();
        for (int attempt = 0; attempt < 8; attempt++) {
            int dx = level.random.nextInt(17) - 8;
            int dz = level.random.nextInt(17) - 8;
            BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    base.offset(dx, 0, dz));
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
}
