package cc.sighs.dreamworld.dream;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import cc.sighs.dreamworld.entity.DreamClayEntity;
import cc.sighs.dreamworld.net.DWPayloads;

/**
 * Dream-side creatures.
 *
 * <p>The two layers share one server world, so a creature that belongs to the dream is marked in
 * its persistent data and only sent to dreaming players; everybody else never receives it. Dream
 * intrusions move creatures between the layers instead of killing them: the original is removed
 * without a death event or loot, and a sleeping copy is created on the opposite layer.</p>
 */
public final class DreamEntities {
    private static final String DREAM_ONLY = "DreamWorldDreamEntity";
    /** Entity IDs already sent to a player, per player UUID. */
    private static final Map<UUID, Set<Integer>> SENT = new HashMap<>();
    private static final double VIEW_DISTANCE_SQR = 64.0D * 64.0D;

    private DreamEntities() {
    }

    public static boolean isDreamOnly(Entity entity) {
        return entity.getPersistentData().getBoolean(DREAM_ONLY);
    }

    /**
     * A creature stops belonging to the dream: it leaves a blob of dream clay behind where it stood.
     * Callers that move a creature back to reality or drop it on cleanup use this instead of
     * clearing the flag directly, so every departure produces the clay.
     */
    public static void leaveDream(Entity entity) {
        if (!isDreamOnly(entity)) {
            return;
        }
        if (entity.level() instanceof ServerLevel level) {
            DreamClayEntity.spawnNear(level, Vec3.atBottomCenterOf(entity.blockPosition()));
        }
        setDreamOnly(entity, false);
    }

    public static void setDreamOnly(Entity entity, boolean dreamOnly) {
        if (dreamOnly) {
            entity.getPersistentData().putBoolean(DREAM_ONLY, true);
        } else {
            entity.getPersistentData().remove(DREAM_ONLY);
        }
    }

    /**
     * Creates a sleeping copy of {@code source} at {@code pos} on the layer chosen by
     * {@code dreamOnly}. Returns {@code null} when the entity type cannot be created.
     */
    @Nullable
    public static Entity sleepingCopy(ServerLevel level, Entity source, BlockPos pos, boolean dreamOnly) {
        Entity copy = source.getType().create(level);
        if (copy == null) {
            return null;
        }
        CompoundTag tag = new CompoundTag();
        source.saveWithoutId(tag);
        copy.load(tag);
        copy.setUUID(UUID.randomUUID());
        Vec3 center = Vec3.atBottomCenterOf(pos);
        copy.moveTo(center.x, center.y, center.z, source.getYRot(), 0.0F);
        copy.setDeltaMovement(Vec3.ZERO);
        copy.setPose(Pose.SLEEPING);
        if (copy instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setPersistenceRequired();
        }
        setDreamOnly(copy, dreamOnly);
        if (!level.addFreshEntity(copy)) {
            copy.discard();
            return null;
        }
        return copy;
    }

    /**
     * Sends or removes the dream creatures a player is allowed to see.
     *
     * <p>The regular tracking loop only re-evaluates an entity when it or the player crosses a
     * chunk section, so a creature created before the dream started would never be sent. This walks
     * the level explicitly on every dream state change and once per second while dreaming.</p>
     */
    public static void refreshVisibility(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        boolean dreaming = DreamManager.isDreaming(player);
        Set<Integer> sent = SENT.computeIfAbsent(player.getUUID(), key -> new HashSet<>());
        Map<Integer, Entity> nearby = new HashMap<>();
        if (dreaming) {
            for (Entity entity : level.getAllEntities()) {
                if (isDreamOnly(entity) && entity.distanceToSqr(player) <= VIEW_DISTANCE_SQR) {
                    nearby.put(entity.getId(), entity);
                }
            }
        }
        for (Iterator<Integer> iterator = sent.iterator(); iterator.hasNext();) {
            int id = iterator.next();
            if (!nearby.containsKey(id)) {
                player.connection.send(new ClientboundRemoveEntitiesPacket(id));
                iterator.remove();
            }
        }
        for (Map.Entry<Integer, Entity> entry : nearby.entrySet()) {
            if (sent.contains(entry.getKey())) {
                continue;
            }
            Entity entity = entry.getValue();
            player.connection.send(new ClientboundAddEntityPacket(entity, 0, entity.blockPosition()));
            List<SynchedEntityData.DataValue<?>> data = entity.getEntityData().getNonDefaultValues();
            if (data != null && !data.isEmpty()) {
                player.connection.send(new ClientboundSetEntityDataPacket(entity.getId(), data));
            }
            sent.add(entry.getKey());
        }
    }

    /** IDs of every dream-only entity of the level, used by the client visibility filter. */
    public static List<Integer> dreamEntityIds(ServerLevel level) {
        List<Integer> ids = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (isDreamOnly(entity)) {
                ids.add(entity.getId());
            }
        }
        return ids;
    }

    /** Pushes the current dream-entity ID list to a single dreaming player. */
    public static void sync(ServerPlayer player) {
        if (!DreamManager.isDreaming(player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        PacketDistributor.sendToPlayer(player,
                new DWPayloads.DreamEntitiesPayload(dreamEntityIds(level)));
    }

    /** Pushes the list to every dreaming player of the level. */
    public static void syncAll(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            sync(player);
        }
    }
}
