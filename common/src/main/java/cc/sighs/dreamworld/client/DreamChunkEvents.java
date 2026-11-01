package cc.sighs.dreamworld.client;

import cc.sighs.dreamworld.entity.DreamClayEntity;
import cc.sighs.dreamworld.entity.BlubEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Applies the dream patch set the moment a chunk is loaded on the client.
 *
 * <p>This is the hook that makes the dream layer a real terrain swap instead of a post-load edit:
 * patches that arrived before their chunk are cached by {@link DreamClientPatches}, and they are
 * written into the freshly loaded chunk before the player can walk on it. Collision, lighting and
 * meshes then all come from the dream blocks, so no movement flags have to be touched.</p>
 *
 * <p>The same tick hook keeps the dreamed population out of a client that is not dreaming: every
 * dream blub is dropped from the client level, so an awake player never has one in their world even
 * for a single frame.</p>
 */
public final class DreamChunkEvents {
    /** Ticks between two sweeps that drop blubs from a non-dreaming level. */
    private static final int BLUB_SWEEP_INTERVAL = 20;
    /**
     * How long the local body may be held while waiting for its chunk's dream state.
     *
     * <p>A safety valve only: the payload is sent together with the chunk, so the wait is normally a
     * few ticks. Holding forever would be worse than a wrong position.</p>
     */
    private static final int MAX_HOLD_TICKS = 100;

    private static boolean lastDreaming;
    private static int sweepTicks;
    /** Position the local body is held at while its chunk's dream state is still on its way. */
    private static Vec3 holdPosition;
    private static int holdTicks;

    private DreamChunkEvents() {
    }

    /**
     * Forgets the previous session's dream state when the client leaves a world.
     *
     * <p>Everything the overlay knows is pushed by the server, but the flags and the patch cache are
     * static and outlive a disconnect. A client that quits while dreaming and joins another world
     * would otherwise paint the old dream layer over the new chunks before the server said anything -
     * including turning the ground under the player into air.</p>
     */
    public static void onLoggingOut() {
        resetSession();
    }

    /** Joining a world starts from a clean slate too, in case the previous session never said goodbye. */
    public static void onLoggingIn() {
        resetSession();
    }

    public static void resetSession() {
        DreamClientState.reset();
        DreamClientPatches.reset();
        DreamHud.reset();
        lastDreaming = false;
        sweepTicks = 0;
        holdPosition = null;
        holdTicks = 0;
    }

    /**
     * Keeps the body still until this client knows which layer it is on and what that layer shows
     * under it.
     *
     * <p>On login the client is simulating the real world for as long as the dream state packet and
     * the chunk's patch set are in flight. Where the dream has a floor and reality does not - which
     * is exactly the terrain the dreamer was standing on when they logged out - the body drops
     * through it, and because a dreamer's movement is trusted, the server makes that drop permanent.
     * The body is therefore pinned in place from the very first tick until both answers are in; then
     * normal collision against the dream layer takes over. Nothing is corrected afterwards, so no
     * position ever has to be guessed.</p>
     */
    public static void onClientTickPre() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null || !shouldHoldBody(player) || holdTicks >= MAX_HOLD_TICKS) {
            holdPosition = null;
            holdTicks = 0;
            return;
        }
        if (holdPosition == null) {
            holdPosition = player.position();
        }
        holdTicks++;
        player.setDeltaMovement(Vec3.ZERO);
        player.setPos(holdPosition.x, holdPosition.y, holdPosition.z);
        // Keep the interpolation anchors on the held spot, or the renderer eases from the old one.
        player.xo = holdPosition.x;
        player.yo = holdPosition.y;
        player.zo = holdPosition.z;
        player.fallDistance = 0.0F;
    }

    /**
     * {@return whether the body has to stay put because the client cannot simulate it yet}
     *
     * <p>Two unknowns qualify. First, the server has not said whether this client is dreaming at all:
     * whatever the body would collide with is a guess. Second, it is dreaming but the patch set of
     * its own chunk has not arrived: the chunk still holds reality, which is not the layer to fall
     * onto.</p>
     */
    private static boolean shouldHoldBody(LocalPlayer player) {
        if (!DreamClientState.isDreamStateKnown()) {
            return true;
        }
        if (!DreamClientState.isDreaming()) {
            return false;
        }
        return !DreamClientPatches.knowsChunk(player.chunkPosition());
    }

    public static void onClientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        boolean dreaming = DreamClientState.isDreaming();
        if (dreaming != lastDreaming) {
            lastDreaming = dreaming;
            if (level != null) {
                // Drop the blobs that belong to the layer the player just left.
                List<Entity> stale = new ArrayList<>();
                for (Entity entity : level.entitiesForRendering()) {
                    if (entity instanceof DreamClayEntity clay && clay.isDreamOrigin() != dreaming) {
                        stale.add(entity);
                    }
                }
                stale.forEach(Entity::discard);
                if (!dreaming) {
                    discardBlubs(level);
                }
                minecraft.levelRenderer.allChanged();
            }
        }
        if (!dreaming && level != null && ++sweepTicks % BLUB_SWEEP_INTERVAL == 0) {
            discardBlubs(level);
        }
    }

    /**
     * Removes every dream blub from a client level that is not showing the dream layer.
     *
     * <p>The server already filters them out of the tracking set of a non-dreaming player, but a
     * blub can still be in the level for a moment - a stale packet, a reconnect, a dream that ended
     * while the entity was in flight. The client is the last word on what is visible, so it simply
     * drops them.</p>
     */
    private static void discardBlubs(ClientLevel level) {
        List<Entity> blubs = null;
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof BlubEntity) {
                if (blubs == null) {
                    blubs = new ArrayList<>();
                }
                blubs.add(entity);
            }
        }
        if (blubs != null) {
            blubs.forEach(Entity::discard);
        }
    }

    public static void onChunkLoad(boolean clientSide, long chunkPos) {
        if (!DreamClientState.isDreaming() || !clientSide) {
            return;
        }
        DreamClientPatches.applyChunk(chunkPos);
    }
}
