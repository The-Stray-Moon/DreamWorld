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

import cc.sighs.dreamworld.entity.DreamClayEntity;
import cc.sighs.dreamworld.net.DreamPackets;
import cc.sighs.dreamworld.spi.DreamPlatform;

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
    /**
     * Marks a dream copy that has to lie down.
     *
     * <p>The pose itself is not part of the entity save data, so without this flag a reloaded dream
     * creature would come back standing. The rabbit is dream-only too but stays upright, which is why
     * the sleeping state is tracked separately.</p>
     */
    private static final String DREAM_SLEEPING = "DreamWorldSleepingCopy";
    /** Entity IDs already sent to a player, per player UUID. */
    private static final Map<UUID, Set<Integer>> SENT = new HashMap<>();
    private static final double VIEW_DISTANCE_SQR = 64.0D * 64.0D;

    private DreamEntities() {
    }

    public static boolean isDreamOnly(Entity entity) {
        return DreamPlatform.get().persistentData().get(entity).getBoolean(DREAM_ONLY);
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
            DreamPlatform.get().persistentData().get(entity).putBoolean(DREAM_ONLY, true);
        } else {
            // Leaving the layer also drops the sleeping mark, so a creature that came back to reality
            // is never laid down again after a reload.
            DreamPlatform.get().persistentData().get(entity).remove(DREAM_ONLY);
            DreamPlatform.get().persistentData().get(entity).remove(DREAM_SLEEPING);
        }
    }

    /**
     * Re-applies the sleeping pose to a dream copy that has just (re)entered the level.
     *
     * <p>The pose is not part of the entity save data, so without this a reloaded intruded creature
     * would stand up again. Only copies marked at intrusion time are affected - the rabbit is
     * dream-only as well and stays upright.</p>
     */
    public static void restoreSleepingPose(Entity entity) {
        if (!isDreamOnly(entity)
                || !DreamPlatform.get().persistentData().get(entity).getBoolean(DREAM_SLEEPING)) {
            return;
        }
        entity.setPose(Pose.SLEEPING);
        if (entity instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setPersistenceRequired();
        }
    }

    /**
     * Creates the copy of {@code source} that represents an intrusion at {@code pos}.
     *
     * <p>Which layer the copy lands on decides how it behaves. A creature carried into the dream
     * arrives asleep and inert, which is how an intruded creature looks there. A creature carried
     * back into reality wakes up and keeps its normal AI, so an intrusion never leaves a mob
     * permanently frozen in the real world.</p>
     *
     * <p>The saved tag is filtered before loading: a dream copy was created asleep, so its sleep
     * position and its disabled AI would otherwise travel along and pin the real-world copy to a bed
     * that does not exist. Returns {@code null} when the entity type cannot be created.</p>
     */
    @Nullable
    public static Entity intrudedCopy(ServerLevel level, Entity source, BlockPos pos, boolean dreamOnly) {
        Entity copy = source.getType().create(level);
        if (copy == null) {
            return null;
        }
        CompoundTag tag = new CompoundTag();
        source.saveWithoutId(tag);
        // A sleeping source would hand its bed down to the copy; each layer decides its own pose.
        tag.remove("SleepingX");
        tag.remove("SleepingY");
        tag.remove("SleepingZ");
        tag.remove("NoAI");
        copy.load(tag);
        copy.setUUID(UUID.randomUUID());
        Vec3 center = Vec3.atBottomCenterOf(pos);
        copy.moveTo(center.x, center.y, center.z, source.getYRot(), 0.0F);
        copy.setDeltaMovement(Vec3.ZERO);
        if (dreamOnly) {
            copy.setPose(Pose.SLEEPING);
            DreamPlatform.get().persistentData().get(copy).putBoolean(DREAM_SLEEPING, true);
            if (copy instanceof Mob mob) {
                mob.setNoAi(true);
                mob.setPersistenceRequired();
            }
        } else {
            copy.setPose(Pose.STANDING);
            if (copy instanceof Mob mob) {
                mob.setNoAi(false);
                mob.setPersistenceRequired();
            }
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
        DreamPlatform.get().network().sendToPlayer(player,
                new DreamPackets.DreamEntities(dreamEntityIds(level)));
    }

    /** Pushes the list to every dreaming player of the level. */
    public static void syncAll(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            sync(player);
        }
    }
}
