package cc.sighs.dreamworld.platform;

import cc.sighs.dreamworld.DWObjects;
import cc.sighs.dreamworld.dream.DreamCommand;
import cc.sighs.dreamworld.dream.DreamDamageRules;
import cc.sighs.dreamworld.dream.DreamMobs;
import cc.sighs.dreamworld.dream.DreamPatchSync;
import cc.sighs.dreamworld.dream.DreamSleepEvents;
import cc.sighs.dreamworld.dream.GraffitiSync;
import cc.sighs.dreamworld.entity.BlubEntity;
import cc.sighs.dreamworld.item.DreamToolEvents;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Server-side Fabric event adapter; it only translates events into shared calls. */
public final class FabricEvents {
    /** Last chunk whose patch set was pushed, so moving between chunks resends on this loader. */
    private static final Map<UUID, Long> LAST_PATCH_CHUNK = new HashMap<>();
    private static int tickCounter;

    private FabricEvents() {
    }

    public static void register() {
        FabricDefaultAttributeRegistry.register(DWObjects.BLUB.get(), BlubEntity.dreamAttributes());

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            DreamSleepEvents.tick(server);
            DreamMobs.tick(server);
            DreamPatchSync.onServerTick(server);
            if (++tickCounter % 20 == 0) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (!DreamManagerAccess.isDreaming(player)
                            || !(player.level() instanceof ServerLevel level)) {
                        continue;
                    }
                    long chunk = player.chunkPosition().toLong();
                    if (!Long.valueOf(chunk).equals(LAST_PATCH_CHUNK.put(player.getUUID(), chunk))) {
                        DreamPatchSync.sendChunk(player, level, player.chunkPosition());
                    }
                }
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            DreamSleepEvents.onLogin(handler.player);
            GraffitiSync.onPlayerLoggedIn(handler.player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                DreamSleepEvents.onLogout(handler.player));

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                DreamSleepEvents.onRespawn(newPlayer));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
                GraffitiSync.onPlayerChangedDimension(player));

        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> DreamMobs.onEntityJoin(entity));

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) ->
                !(world instanceof ServerLevel level && player instanceof ServerPlayer serverPlayer
                        && DreamToolEvents.onBlockBreak(serverPlayer, level, pos)));

        ServerLivingEntityEvents.ALLOW_DAMAGE.register(FabricEvents::allowDamage);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                DreamCommand.register(dispatcher));
    }

    private static boolean allowDamage(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source,
            float amount) {
        if (DreamToolEvents.onIncomingDamage(entity, source, amount)) {
            return false;
        }
        return !DreamDamageRules.shouldCancel(entity);
    }

    /** Keeps the shared dream-flag lookup out of this adapter's imports. */
    private static final class DreamManagerAccess {
        private DreamManagerAccess() {
        }

        static boolean isDreaming(ServerPlayer player) {
            return cc.sighs.dreamworld.dream.DreamManager.isDreaming(player);
        }
    }
}
