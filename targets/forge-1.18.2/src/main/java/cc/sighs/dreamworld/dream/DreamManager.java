package cc.sighs.dreamworld.dream;

import cc.sighs.dreamworld.entity.DreamSpearEntity;
import cc.sighs.dreamworld.net.DreamPackets;
import cc.sighs.dreamworld.spi.DreamPlatform;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server-side dream state of a player.
 *
 * <p>The dreamer never leaves the real level: the server keeps the player exactly where it is, in a
 * completely normal pose and state, and the dream itself is a client-side view of the level built
 * from {@link DreamPatches}. The sleeping body exists only on the clients of the other players, which
 * render a dreaming player lying down from the broadcast dreamer list.</p>
 *
 * <p>The optional anchor of a thrown dream alloy spear is bound to the layer it was thrown on and
 * decides everything that crosses the mirror: where an intrusion lands on the other side, and where
 * the dreamer comes back when waking. A spear thrown in reality pulls the dreamer out of the dream
 * and pins dream-side intrusions; a spear thrown inside the dream pins real-side intrusions. Spears
 * stuck in a creature follow that creature instead of a fixed position.</p>
 */
public final class DreamManager {
    private static final String ROOT = "DreamWorld";
    private static final String DREAMING = "Dreaming";
    private static final String ANCHOR_DIM = "AnchorDim";
    private static final String ANCHOR_POS = "AnchorPos";
    private static final String ANCHOR_ENTITY = "AnchorEntity";
    /** Layer the anchor lives on, so each side's intrusions can land on the other one. */
    private static final String ANCHOR_DREAM_SIDE = "AnchorDreamSide";
    /** How many blocks a buried body may be lifted during login before giving up. */
    private static final int SETTLE_SCAN_HEIGHT = 32;

    private DreamManager() {
    }

    // ------------------------------------------------------------ dream flag

    public static boolean isDreaming(ServerPlayer player) {
        return tag(player).getBoolean(DREAMING);
    }

    /** Low-level flag write; callers normally use {@link #enterDream} / {@link #wakeUp}. */
    public static void setDreaming(ServerPlayer player, boolean dreaming) {
        tag(player).putBoolean(DREAMING, dreaming);
        pushDreamState(player);
        // Dream rabbits are filtered out of the tracking set of non-dreaming players, so the set
        // has to be re-evaluated here to send or remove them right away.
        DreamMobs.refreshTracking(player);
        // Other clients draw this player lying down; the server and the dreamer's own client never
        // do, so the pose is not touched here and each client is told the current set instead.
        MinecraftServer server = player.getServer();
        if (server != null) {
            broadcastDreamers(server);
        }
    }

    /**
     * Tells one client which layer it should be showing.
     *
     * <p>Sent on every login, dreaming or not: the client holds the body still until this packet
     * arrives, because without it there is no telling which layer the body would be colliding with.
     * </p>
     */
    public static void pushDreamState(ServerPlayer player) {
        DreamPlatform.get().network().sendToPlayer(player, new DreamPackets.DreamState(isDreaming(player)));
    }

    /**
     * Pushes the set of dreaming players to every client.
     *
     * <p>The sleeping look is a rendering decision made by the observers: the dreamer keeps walking
     * around in a normal pose as far as the server and their own client are concerned. Everyone else
     * is handed the list and renders those players as if they were asleep.</p>
     */
    public static void broadcastDreamers(MinecraftServer server) {
        List<UUID> dreamers = new ArrayList<>();
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (isDreaming(online)) {
                dreamers.add(online.getUUID());
            }
        }
        if (server != null) {
            DreamPlatform.get().network().sendToAll(server, new DreamPackets.Dreamers(dreamers));
        }
    }

    /**
     * Starts the dream.
     *
     * <p>The body starts where it is unless a spear was thrown inside the dream: that anchor names a
     * dream position, so entering the dream has to move the body there for the promise "the dream
     * always starts at the anchor" to hold. A spear thrown in reality is not consulted here - it is
     * the way back out, not the way in.</p>
     */
    public static void enterDream(ServerPlayer player) {
        if (isDreaming(player)) {
            return;
        }
        refreshAnchor(player);
        setDreaming(player, true);
        Destination destination = anchorDestination(player, true);
        if (destination != null) {
            // The anchor was picked by this player inside the dream, so it is a valid dream spot even
            // if reality holds a block there; settling afterwards would drag the body off it.
            teleport(player, destination.level(), destination.pos(), true);
        } else {
            // The dream layer may have a block where reality has none; the body has to leave it or
            // the client draws it inside the ground.
            settleOutOfTerrain(player);
        }
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
        DreamEntities.refreshVisibility(player);
        if (player.level instanceof ServerLevel level) {
            // Nobody may be left in the dream layer: its rabbits go with the last dreamer.
            DreamMobs.discardIfNoDreamers(level);
        }
        Destination destination = anchorDestination(player, false);
        if (destination != null) {
            teleport(player, destination.level(), destination.pos());
        }
    }

    /**
     * Destination of the bound anchor on one side of the mirror.
     *
     * <p>An anchor names a position on its own layer, so each direction only honours the anchor of
     * the opposite side: a spear thrown in reality brings the dreamer back out, a spear thrown inside
     * the dream brings them in. A block anchor resolves to its stored position, an entity anchor to
     * wherever that entity currently stands.</p>
     *
     * @param dreamSide whether the destination is wanted for entering the dream
     */
    @Nullable
    private static Destination anchorDestination(ServerPlayer player, boolean dreamSide) {
        MinecraftServer server = player.getServer();
        if (server == null || isAnchorOnDreamSide(player) != dreamSide) {
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
            if (anchor != null && anchor.level instanceof ServerLevel level) {
                return new Destination(level, anchor.blockPosition());
            }
        }
        return null;
    }

    /** Teleports without suffocating: a solid destination block moves the player one block up. */
    public static void teleport(ServerPlayer player, ServerLevel target, BlockPos pos) {
        teleport(player, target, pos, false);
    }

    /**
     * Teleports to a position and picks the obstacle test from the layer it belongs to.
     *
     * <p>A dream position has to be judged by the dream terrain: reality may hold a block exactly
     * there while the dream shows air, and lifting the body out of a block it cannot even see would
     * move it off the anchor it was told to arrive at.</p>
     */
    private static void teleport(ServerPlayer player, ServerLevel target, BlockPos pos,
            boolean dreamSide) {
        BlockState state = dreamSide
                ? DreamPatches.get(target).stateAt(target, pos)
                : target.getBlockState(pos);
        boolean blocked = !state.getCollisionShape(target, pos).isEmpty();
        double y = blocked ? pos.getY() + 1.0D : pos.getY();
        // 1.18.2 teleports without the relative-movement set.
        player.teleportTo(target, pos.getX() + 0.5D, y, pos.getZ() + 0.5D,
                player.getYRot(), player.getXRot());
    }

    private record Destination(ServerLevel level, BlockPos pos) {
    }

    /**
     * Lifts a body that is standing inside terrain out of it.
     *
     * <p>While dreaming the body follows the client, and the client collides with the dream layer
     * while the server keeps reality. The two disagree wherever the world was edited, so a body can
     * be saved inside a real block - or inside a dream block, which the client then draws around the
     * player. The body is moved to the first spot above it that is free on both layers.</p>
     *
     * <p>Both layers are checked on purpose: a spot that is only free in reality would still be
     * rendered as a block around the player, and the client would push them out again.</p>
     */
    public static void settleOutOfTerrain(ServerPlayer player) {
        if (!(player.level instanceof ServerLevel level)) {
            return;
        }
        BlockPos feet = player.blockPosition();
        if (isFreeOnBothLayers(level, feet) && isFreeOnBothLayers(level, feet.above())) {
            return;
        }
        for (int lift = 1; lift <= SETTLE_SCAN_HEIGHT; lift++) {
            BlockPos candidate = feet.above(lift);
            if (isFreeOnBothLayers(level, candidate)
                    && isFreeOnBothLayers(level, candidate.above())) {
                teleport(player, level, candidate);
                return;
            }
        }
    }

    /** {@return whether {@code pos} is air on the real layer and on the dream layer} */
    private static boolean isFreeOnBothLayers(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && DreamPatches.get(level).stateAt(level, pos).getCollisionShape(level, pos).isEmpty();
    }

    /**
     * Rebinds the anchor from the player's anchored dream alloy spear, or clears it when the spear
     * is gone. A spear stuck in an entity anchors that entity, a spear lying in the world anchors
     * its block position, and the layer the spear was thrown on is remembered with it. Spears of
     * other players are ignored.
     */
    public static void refreshAnchor(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        UUID ownerId = player.getUUID();
        for (ServerLevel serverLevel : server.getAllLevels()) {
            for (Entity entity : serverLevel.getEntities().getAll()) {
                if (entity instanceof DreamSpearEntity spear
                        && ownerId.equals(spear.owner())
                        && spear.isAnchored()) {
                    bindSpearAnchor(player, spear, serverLevel);
                    return;
                }
            }
        }
        clearAnchor(player);
    }

    /** Copies one spear's anchor data onto its owner. */
    private static void bindSpearAnchor(ServerPlayer player, DreamSpearEntity spear,
            ServerLevel level) {
        UUID stuckIn = spear.anchoredEntity();
        if (stuckIn != null) {
            setEntityAnchor(player, stuckIn, spear.dreamSide());
        } else {
            setBlockAnchor(player, level.dimension(), spear.blockPosition(), spear.dreamSide());
        }
    }

    // --------------------------------------------------------------- anchor

    public static boolean hasAnchor(ServerPlayer player) {
        CompoundTag tag = tag(player);
        return tag.contains(ANCHOR_POS) || tag.hasUUID(ANCHOR_ENTITY);
    }

    /**
     * Stores a block anchor together with the layer it was thrown on.
     *
     * <p>The layer matters from both ends: an intrusion on the dream side lands on a real-side
     * anchor, and an intrusion on the real side lands on a dream-side anchor. Without it a spear
     * thrown inside the dream could not pin anything, because the coordinates it names only make
     * sense as a dream position.</p>
     */
    public static void setBlockAnchor(ServerPlayer player, ResourceKey<Level> dimension, BlockPos pos,
            boolean dreamSide) {
        CompoundTag tag = tag(player);
        tag.putString(ANCHOR_DIM, dimension.location().toString());
        tag.putLong(ANCHOR_POS, pos.asLong());
        tag.putBoolean(ANCHOR_DREAM_SIDE, dreamSide);
        tag.remove(ANCHOR_ENTITY);
        pushAnchorState(player);
    }

    public static void setEntityAnchor(ServerPlayer player, UUID entityId, boolean dreamSide) {
        CompoundTag tag = tag(player);
        tag.putUUID(ANCHOR_ENTITY, entityId);
        tag.putBoolean(ANCHOR_DREAM_SIDE, dreamSide);
        tag.remove(ANCHOR_POS);
        tag.remove(ANCHOR_DIM);
        pushAnchorState(player);
    }

    public static void clearAnchor(ServerPlayer player) {
        CompoundTag tag = tag(player);
        tag.remove(ANCHOR_DIM);
        tag.remove(ANCHOR_POS);
        tag.remove(ANCHOR_ENTITY);
        tag.remove(ANCHOR_DREAM_SIDE);
        pushAnchorState(player);
    }

    /**
     * Tells the owning client whether an anchor is bound.
     *
     * <p>The hotbar hint and the refusal to start a throw both live on the client, and the client
     * cannot work the answer out on its own: the anchored spear is a server entity that may sit far
     * away or in a chunk the client does not track. Login re-pushes it, because the flag is derived
     * from saved data.</p>
     */
    public static void pushAnchorState(ServerPlayer player) {
        DreamPlatform.get().network().sendToPlayer(player,
                new DreamPackets.AnchorState(hasAnchor(player)));
    }

    /** {@return whether the bound anchor lives on the dream layer} */
    public static boolean isAnchorOnDreamSide(ServerPlayer player) {
        return tag(player).getBoolean(ANCHOR_DREAM_SIDE);
    }

    public static Optional<ResourceKey<Level>> anchorDimension(ServerPlayer player) {
        CompoundTag tag = tag(player);
        if (!tag.contains(ANCHOR_DIM)) {
            return Optional.empty();
        }
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(ANCHOR_DIM));
        return id == null ? Optional.empty()
                : Optional.of(ResourceKey.create(Registry.DIMENSION_REGISTRY, id));
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

    /**
     * Resolves where an intrusion lands.
     *
     * <p>The anchor names a position on its own layer, so it only replaces {@code at} when the actor
     * intrudes on the other layer - a spear thrown in reality pins dream-side intrusions, a spear
     * thrown in the dream pins real-side ones. An entity anchor resolves to wherever that entity
     * currently stands, which is what makes "anchor to a creature" follow it around.</p>
     */
    public static BlockPos intrusionTarget(ServerPlayer player, BlockPos at) {
        if (!hasAnchor(player) || isAnchorOnDreamSide(player) == isDreaming(player)) {
            // No anchor, or an anchor on the same layer as the intrusion: nothing to redirect to.
            return at;
        }
        Optional<BlockPos> anchorPos = anchorPos(player);
        if (anchorPos.isPresent()) {
            return anchorPos.get();
        }
        Optional<UUID> anchorEntity = anchorEntity(player);
        MinecraftServer server = player.getServer();
        if (anchorEntity.isPresent() && server != null) {
            Entity entity = findEntity(server, anchorEntity.get());
            if (entity != null) {
                return entity.blockPosition();
            }
        }
        return at;
    }

    @Nullable
    public static ServerLevel level(ServerPlayer player) {
        return player.level instanceof ServerLevel serverLevel ? serverLevel : null;
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
        CompoundTag persistent = DreamPlatform.get().persistentData().get(player);
        if (!persistent.contains(ROOT)) {
            persistent.put(ROOT, new CompoundTag());
        }
        return persistent.getCompound(ROOT);
    }
}
