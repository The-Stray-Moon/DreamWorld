package cc.sighs.dreamworld.dream;

import cc.sighs.dreamworld.entity.DreamSpearEntity;
import cc.sighs.dreamworld.net.DWPayloads;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server-side dream state of a player.
 *
 * <p>The dreamer never leaves the real level: the server keeps the player exactly where it is, so
 * other clients still see the body lying there and moving. The dream itself is a client-side view
 * of the level built from {@link DreamPatches}. The optional anchor of a thrown dream alloy spear
 * decides where the dreamer comes back when waking.</p>
 */
public final class DreamManager {
    private static final String ROOT = "DreamWorld";
    private static final String DREAMING = "Dreaming";
    private static final String ANCHOR_DIM = "AnchorDim";
    private static final String ANCHOR_POS = "AnchorPos";
    private static final String ANCHOR_ENTITY = "AnchorEntity";

    private DreamManager() {
    }

    // ------------------------------------------------------------ dream flag

    public static boolean isDreaming(ServerPlayer player) {
        return tag(player).getBoolean(DREAMING);
    }

    /** Low-level flag write; callers normally use {@link #enterDream} / {@link #wakeUp}. */
    public static void setDreaming(ServerPlayer player, boolean dreaming) {
        tag(player).putBoolean(DREAMING, dreaming);
        player.setForcedPose(dreaming ? Pose.SLEEPING : null);
        PacketDistributor.sendToPlayer(player, new DWPayloads.DreamStatePayload(dreaming));
        // Dream rabbits are filtered out of the tracking set of non-dreaming players, so the set
        // has to be re-evaluated here to send or remove them right away.
        DreamMobs.refreshTracking(player);
    }

    /** Starts the dream in place: no teleport, the client switches to the patched dream layer. */
    public static void enterDream(ServerPlayer player) {
        if (isDreaming(player)) {
            return;
        }
        refreshAnchor(player);
        setDreaming(player, true);
        applyDreamBody(player);
        DreamPatchSync.sendAround(player);
        DreamEntities.sync(player);
        DreamEntities.refreshVisibility(player);
    }

    /** Ends the dream. The body only moves when an anchor is bound. */
    public static void wakeUp(ServerPlayer player) {
        if (!isDreaming(player)) {
            return;
        }
        setDreaming(player, false);
        restoreDreamBody(player);
        DreamEntities.refreshVisibility(player);
        if (player.level() instanceof ServerLevel level) {
            // Nobody may be left in the dream layer: its rabbits go with the last dreamer.
            DreamMobs.discardIfNoDreamers(level);
        }
        Destination destination = anchorDestination(player);
        if (destination != null) {
            teleport(player, destination.level(), destination.pos());
        }
    }

    /** Destination of the bound anchor (block first, then the entity the spear is stuck in). */
    @Nullable
    private static Destination anchorDestination(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return null;
        }
        Optional<BlockPos> anchorPos = anchorPos(player);
        Optional<ResourceKey<Level>> anchorDim = anchorDimension(player);
        if (anchorPos.isPresent() && anchorDim.isPresent()) {
            ServerLevel level = server.getLevel(anchorDim.get());
            if (level != null) {
                return new Destination(level, anchorPos.get());
            }
        }
        Optional<UUID> anchorEntity = anchorEntity(player);
        if (anchorEntity.isPresent()) {
            Entity anchor = findEntity(server, anchorEntity.get());
            if (anchor != null && anchor.level() instanceof ServerLevel level) {
                return new Destination(level, anchor.blockPosition());
            }
        }
        return null;
    }

    /** Teleports without suffocating: a solid destination block moves the player one block up. */
    public static void teleport(ServerPlayer player, ServerLevel target, BlockPos pos) {
        boolean blocked = !target.getBlockState(pos).getCollisionShape(target, pos).isEmpty();
        double y = blocked ? pos.getY() + 1.0D : pos.getY();
        player.teleportTo(target, pos.getX() + 0.5D, y, pos.getZ() + 0.5D,
                Set.<RelativeMovement>of(), player.getYRot(), player.getXRot());
    }

    private record Destination(ServerLevel level, BlockPos pos) {
    }

    // ---------------------------------------------------------- dream movement

    /** A dreamer lies down on the shared side; a rider keeps the vehicle pose instead. */
    public static void applyDreamBody(ServerPlayer player) {
        if (!isDreaming(player)) {
            return;
        }
        // Movement stays completely vanilla: the dream terrain is swapped in on the client, so the
        // dreamer walks on dream blocks with normal collision and gravity.
        player.setForcedPose(player.isPassenger() ? null : Pose.SLEEPING);
    }

    /** Clears the forced pose when the dream ends. */
    public static void restoreDreamBody(ServerPlayer player) {
        player.setForcedPose(null);
    }

    /** Kept for the sleep hooks that only need the pose. */
    public static void applyDreamPose(ServerPlayer player) {
        applyDreamBody(player);
    }

    /**
     * Rebinds the anchor from the player's anchored dream alloy spear, or clears it when the spear
     * is gone. A spear stuck in an entity anchors that entity, a spear lying in the world anchors
     * its block position. Spears of other players are ignored.
     */
    public static void refreshAnchor(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        UUID ownerId = player.getUUID();
        for (ServerLevel serverLevel : server.getAllLevels()) {
            for (Entity entity : serverLevel.getAllEntities()) {
                if (entity instanceof DreamSpearEntity spear
                        && ownerId.equals(spear.owner())
                        && spear.isAnchored()) {
                    UUID stuckIn = spear.anchoredEntity();
                    if (stuckIn != null) {
                        setEntityAnchor(player, stuckIn);
                    } else {
                        setBlockAnchor(player, serverLevel.dimension(), spear.blockPosition());
                    }
                    return;
                }
            }
        }
        clearAnchor(player);
    }

    // --------------------------------------------------------------- anchor

    public static boolean hasAnchor(ServerPlayer player) {
        CompoundTag tag = tag(player);
        return tag.contains(ANCHOR_POS) || tag.hasUUID(ANCHOR_ENTITY);
    }

    public static void setBlockAnchor(ServerPlayer player, ResourceKey<Level> dimension, BlockPos pos) {
        CompoundTag tag = tag(player);
        tag.putString(ANCHOR_DIM, dimension.location().toString());
        tag.putLong(ANCHOR_POS, pos.asLong());
        tag.remove(ANCHOR_ENTITY);
    }

    public static void setEntityAnchor(ServerPlayer player, UUID entityId) {
        CompoundTag tag = tag(player);
        tag.putUUID(ANCHOR_ENTITY, entityId);
        tag.remove(ANCHOR_POS);
        tag.remove(ANCHOR_DIM);
    }

    public static void clearAnchor(ServerPlayer player) {
        CompoundTag tag = tag(player);
        tag.remove(ANCHOR_DIM);
        tag.remove(ANCHOR_POS);
        tag.remove(ANCHOR_ENTITY);
    }

    public static Optional<ResourceKey<Level>> anchorDimension(ServerPlayer player) {
        CompoundTag tag = tag(player);
        if (!tag.contains(ANCHOR_DIM)) {
            return Optional.empty();
        }
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(ANCHOR_DIM));
        return id == null ? Optional.empty()
                : Optional.of(ResourceKey.create(Registries.DIMENSION, id));
    }

    public static Optional<BlockPos> anchorPos(ServerPlayer player) {
        CompoundTag tag = tag(player);
        if (!tag.contains(ANCHOR_POS)) {
            return Optional.empty();
        }
        return Optional.of(BlockPos.of(tag.getLong(ANCHOR_POS)));
    }

    public static Optional<UUID> anchorEntity(ServerPlayer player) {
        CompoundTag tag = tag(player);
        return tag.hasUUID(ANCHOR_ENTITY) ? Optional.of(tag.getUUID(ANCHOR_ENTITY)) : Optional.empty();
    }

    /** Resolves where an intrusion lands: the anchor position when one is set. */
    public static BlockPos intrusionTarget(ServerPlayer player, BlockPos at) {
        return anchorPos(player).orElse(at);
    }

    @Nullable
    public static ServerLevel level(ServerPlayer player) {
        return player.level() instanceof ServerLevel serverLevel ? serverLevel : null;
    }

    @Nullable
    private static Entity findEntity(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    private static CompoundTag tag(ServerPlayer player) {
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(ROOT)) {
            persistent.put(ROOT, new CompoundTag());
        }
        return persistent.getCompound(ROOT);
    }
}
