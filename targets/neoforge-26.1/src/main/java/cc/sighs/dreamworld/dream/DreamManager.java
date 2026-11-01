package cc.sighs.dreamworld.dream;

import cc.sighs.dreamworld.entity.DreamSpearEntity;
import cc.sighs.dreamworld.net.DreamPackets;
import cc.sighs.dreamworld.spi.DreamPlatform;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
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
 * <p>Each layer can hold one anchor of a thrown dream alloy spear. An anchor does not name a
 * transfer position: it names where the player stands on that layer, and an intrusion keeps the
 * player-relative offset when it crosses the mirror. A spear stuck in a creature follows that
 * creature instead of a fixed position.</p>
 */
public final class DreamManager {
    private static final String ROOT = "DreamWorld";
    private static final String DREAMING = "Dreaming";
    private static final String REAL_ANCHOR_DIM = "RealAnchorDim";
    private static final String REAL_ANCHOR_POS = "RealAnchorPos";
    private static final String REAL_ANCHOR_ENTITY = "RealAnchorEntity";
    private static final String DREAM_ANCHOR_DIM = "DreamAnchorDim";
    private static final String DREAM_ANCHOR_POS = "DreamAnchorPos";
    private static final String DREAM_ANCHOR_ENTITY = "DreamAnchorEntity";
    /** How many blocks a buried body may be lifted during login before giving up. */
    private static final int SETTLE_SCAN_HEIGHT = 32;

    private DreamManager() {
    }

    // ------------------------------------------------------------ dream flag

    public static boolean isDreaming(ServerPlayer player) {
        return tag(player).getBooleanOr(DREAMING, false);
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
        MinecraftServer server = player.level().getServer();
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
     * <p>The body moves to the player's reference position on the dream side: the dream-side anchor
     * when there is one, otherwise the real-side anchor mirrored by the shared coordinates. Without
     * any anchor the body stays where it is.</p>
     */
    public static void enterDream(ServerPlayer player) {
        if (isDreaming(player)) {
            return;
        }
        refreshAnchor(player);
        setDreaming(player, true);
        Destination destination = reference(player, true);
        if (destination != null) {
            // The anchor was placed by this player, so it is a valid dream spot even if reality
            // holds a block there; settling afterwards would drag the body off it.
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
        if (player.level() instanceof ServerLevel level) {
            // Nobody may be left in the dream layer: its rabbits go with the last dreamer.
            DreamMobs.discardIfNoDreamers(level);
        }
        Destination destination = reference(player, false);
        if (destination != null) {
            teleport(player, destination.level(), destination.pos());
        }
    }

    /**
     * The player's reference position on one layer.
     *
     * <p>The anchor of that layer wins. When only the other layer has one, the shared coordinates
     * mirror it here: a spear at home in reality also makes home the dream-side reference. Without
     * any anchor the body itself is the reference. A block anchor resolves to its stored position,
     * an entity anchor to wherever that entity currently stands.</p>
     *
     * @param dreamSide whether the reference is wanted for the dream layer
     */
    @Nullable
    private static Destination reference(ServerPlayer player, boolean dreamSide) {
        Destination own = anchorOnSide(player, dreamSide);
        return own != null ? own : anchorOnSide(player, !dreamSide);
    }

    @Nullable
    private static Destination anchorOnSide(ServerPlayer player, boolean dreamSide) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return null;
        }
        Optional<UUID> anchorEntity = anchorEntity(player, dreamSide);
        if (anchorEntity.isPresent()) {
            Entity anchor = findEntity(server, anchorEntity.get());
            if (anchor != null && anchor.level() instanceof ServerLevel level) {
                return new Destination(level, anchor.blockPosition());
            }
            return null;
        }
        Optional<BlockPos> anchorPos = anchorPos(player, dreamSide);
        if (anchorPos.isEmpty()) {
            return null;
        }
        Optional<ResourceKey<Level>> anchorDim = anchorDimension(player, dreamSide);
        ServerLevel level = anchorDim.map(server::getLevel).orElse(null);
        if (level == null && player.level() instanceof ServerLevel current) {
            level = current;
        }
        return level == null ? null : new Destination(level, anchorPos.get());
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
    private static void teleport(ServerPlayer player, ServerLevel target, BlockPos pos, boolean dreamSide) {
        BlockState state = dreamSide
                ? DreamPatches.get(target).stateAt(target, pos)
                : target.getBlockState(pos);
        boolean blocked = !state.getCollisionShape(target, pos).isEmpty();
        double y = blocked ? pos.getY() + 1.0D : pos.getY();
        player.teleportTo(target, pos.getX() + 0.5D, y, pos.getZ() + 0.5D,
                Set.<Relative>of(), player.getYRot(), player.getXRot(), false);
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
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos feet = player.blockPosition();
        if (isFreeOnBothLayers(level, feet) && isFreeOnBothLayers(level, feet.above())) {
            return;
        }
        for (int lift = 1; lift <= SETTLE_SCAN_HEIGHT; lift++) {
            BlockPos candidate = feet.above(lift);
            if (isFreeOnBothLayers(level, candidate) && isFreeOnBothLayers(level, candidate.above())) {
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
     * Rebinds one anchor per layer from the player's anchored dream alloy spears. A spear stuck in
     * an entity anchors that entity, a spear lying in the world anchors its block position, and the
     * layer the spear was thrown on decides which slot it fills. Spears of other players are ignored.
     */
    public static void refreshAnchor(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        UUID ownerId = player.getUUID();
        boolean[] found = new boolean[2];
        for (ServerLevel serverLevel : server.getAllLevels()) {
            for (Entity entity : serverLevel.getAllEntities()) {
                if (!(entity instanceof DreamSpearEntity spear)
                        || !ownerId.equals(spear.owner())
                        || !spear.isAnchored()) {
                    continue;
                }
                int slot = spear.dreamSide() ? 1 : 0;
                if (found[slot]) {
                    continue;
                }
                found[slot] = true;
                UUID stuckIn = spear.anchoredEntity();
                if (stuckIn != null) {
                    writeEntityAnchor(player, stuckIn, spear.dreamSide());
                } else {
                    writeBlockAnchor(player, serverLevel.dimension(), spear.blockPosition(),
                            spear.dreamSide());
                }
            }
        }
        if (!found[0]) {
            removeAnchor(player, false);
        }
        if (!found[1]) {
            removeAnchor(player, true);
        }
        pushAnchorState(player);
    }

    // --------------------------------------------------------------- anchor

    /** {@return whether the layer has an anchor bound} */
    public static boolean hasAnchor(ServerPlayer player, boolean dreamSide) {
        CompoundTag tag = tag(player);
        return tag.contains(posKey(dreamSide))
                || tag.read(entityKey(dreamSide), UUIDUtil.CODEC).isPresent();
    }

    /** {@return whether either layer has an anchor bound} */
    public static boolean hasAnchor(ServerPlayer player) {
        return hasAnchor(player, false) || hasAnchor(player, true);
    }

    /** Stores a block anchor in the slot of the layer it was thrown on. */
    public static void setBlockAnchor(ServerPlayer player, ResourceKey<Level> dimension, BlockPos pos,
            boolean dreamSide) {
        writeBlockAnchor(player, dimension, pos, dreamSide);
        pushAnchorState(player);
    }

    private static void writeBlockAnchor(ServerPlayer player, ResourceKey<Level> dimension,
            BlockPos pos, boolean dreamSide) {
        CompoundTag tag = tag(player);
        tag.putString(dimKey(dreamSide), dimension.identifier().toString());
        tag.putLong(posKey(dreamSide), pos.asLong());
        tag.remove(entityKey(dreamSide));
    }

    /** Stores an entity anchor in the slot of the layer it was thrown on. */
    public static void setEntityAnchor(ServerPlayer player, UUID entityId, boolean dreamSide) {
        writeEntityAnchor(player, entityId, dreamSide);
        pushAnchorState(player);
    }

    private static void writeEntityAnchor(ServerPlayer player, UUID entityId, boolean dreamSide) {
        CompoundTag tag = tag(player);
        tag.store(entityKey(dreamSide), UUIDUtil.CODEC, entityId);
        tag.remove(posKey(dreamSide));
        tag.remove(dimKey(dreamSide));
    }

    /** Clears the anchor of one layer, leaving the other layer's anchor alone. */
    public static void clearAnchor(ServerPlayer player, boolean dreamSide) {
        removeAnchor(player, dreamSide);
        pushAnchorState(player);
    }

    /** Clears both anchors. */
    public static void clearAnchor(ServerPlayer player) {
        removeAnchor(player, false);
        removeAnchor(player, true);
        pushAnchorState(player);
    }

    private static void removeAnchor(ServerPlayer player, boolean dreamSide) {
        CompoundTag tag = tag(player);
        tag.remove(dimKey(dreamSide));
        tag.remove(posKey(dreamSide));
        tag.remove(entityKey(dreamSide));
    }

    /**
     * Tells the owning client which layers have an anchor bound.
     *
     * <p>The hotbar hint and the refusal to start a throw both live on the client, and the client
     * cannot work the answer out on its own: the anchored spear is a server entity that may sit far
     * away or in a chunk the client does not track. Login re-pushes it, because the flags are derived
     * from the anchored spears.</p>
     */
    public static void pushAnchorState(ServerPlayer player) {
        DreamPlatform.get().network().sendToPlayer(player,
                new DreamPackets.AnchorState(hasAnchor(player, false), hasAnchor(player, true)));
    }

    public static Optional<ResourceKey<Level>> anchorDimension(ServerPlayer player, boolean dreamSide) {
        CompoundTag tag = tag(player);
        String key = dimKey(dreamSide);
        if (!tag.contains(key)) {
            return Optional.empty();
        }
        Identifier id = Identifier.tryParse(tag.getStringOr(key, ""));
        return id == null ? Optional.empty()
                : Optional.of(ResourceKey.create(Registries.DIMENSION, id));
    }

    public static Optional<BlockPos> anchorPos(ServerPlayer player, boolean dreamSide) {
        CompoundTag tag = tag(player);
        String key = posKey(dreamSide);
        if (!tag.contains(key)) {
            return Optional.empty();
        }
        return Optional.of(BlockPos.of(tag.getLongOr(key, 0L)));
    }

    public static Optional<UUID> anchorEntity(ServerPlayer player, boolean dreamSide) {
        return tag(player).read(entityKey(dreamSide), UUIDUtil.CODEC);
    }

    /** {@return the block position the player occupies on {@code dreamSide}} */
    public static BlockPos referencePos(ServerPlayer player, boolean dreamSide) {
        Destination reference = reference(player, dreamSide);
        return reference != null ? reference.pos() : player.blockPosition();
    }

    /**
     * Resolves where an intrusion lands.
     *
     * <p>The offset of {@code at} from the player's body is kept, and the reference position of the
     * target layer becomes the new origin: breaking a block next to the player on one layer places
     * its copy next to the player on the other one. The anchor changes that origin, not the offset,
     * which is what lets a single spear at home carry a distant tower home.</p>
     */
    public static BlockPos intrusionTarget(ServerPlayer player, BlockPos at) {
        BlockPos body = player.blockPosition();
        BlockPos reference = referencePos(player, !isDreaming(player));
        if (reference.equals(body)) {
            return at;
        }
        return new BlockPos(
                at.getX() + reference.getX() - body.getX(),
                at.getY() + reference.getY() - body.getY(),
                at.getZ() + reference.getZ() - body.getZ());
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

    private static String dimKey(boolean dreamSide) {
        return dreamSide ? DREAM_ANCHOR_DIM : REAL_ANCHOR_DIM;
    }

    private static String posKey(boolean dreamSide) {
        return dreamSide ? DREAM_ANCHOR_POS : REAL_ANCHOR_POS;
    }

    private static String entityKey(boolean dreamSide) {
        return dreamSide ? DREAM_ANCHOR_ENTITY : REAL_ANCHOR_ENTITY;
    }

    private static CompoundTag tag(ServerPlayer player) {
        CompoundTag persistent = DreamPlatform.get().persistentData().get(player);
        if (!persistent.contains(ROOT)) {
            persistent.put(ROOT, new CompoundTag());
        }
        return persistent.getCompoundOrEmpty(ROOT);
    }
}
