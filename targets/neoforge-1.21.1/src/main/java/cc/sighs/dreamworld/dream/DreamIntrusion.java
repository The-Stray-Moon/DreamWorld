package cc.sighs.dreamworld.dream;

import cc.sighs.dreamworld.block.DreamGraffitiBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Dream intrusion, expressed as dream patch edits.
 *
 * <p>The dream layer is the real level plus {@link DreamPatches}. The transfer therefore runs in
 * both directions with the same pieces: when a block changes in the real world, the recorder keeps
 * the old state in the patch, so the dream still shows it (the block "moved" to the dream side);
 * when a dreamer breaks a block, the patch of that position becomes air and the state that was
 * broken is carried into the real world at the dreamer's intrusion target.</p>
 *
 * <p>A creature hit by the dream alloy sword never dies on either side: damage is cancelled by the
 * tool hook and the mob simply falls asleep where it stands, which is how an intruded creature
 * looks.</p>
 */
public final class DreamIntrusion {
    private DreamIntrusion() {
    }

    /**
     * Applies a dream-side break: the block leaves the dream layer and is carried into the real
     * world at the intruder's target, overwriting whatever sits there.
     *
     * <p>A real-world break needs nothing here: whoever breaks the block removes it, and the patch
     * recorder keeps its previous state for the dream layer. Nothing is dropped and no experience
     * is spawned on either path.</p>
     */
    public static void breakBlock(ServerPlayer actor, ServerLevel level, BlockPos pos) {
        if (!DreamManager.isDreaming(actor)) {
            // Real-world break: the transfer to the dream side is the recorder's job.
            return;
        }
        BlockState carried = level.getBlockState(pos);
        if (carried.isAir()) {
            return;
        }
        // Graffiti belongs to both sides through its registry, an intrusion must not erase it.
        if (carried.getBlock() instanceof DreamGraffitiBlock) {
            return;
        }
        DreamPatches.get(level).setDreamState(level, pos, Blocks.AIR.defaultBlockState());
        BlockPos target = DreamManager.intrusionTarget(actor, pos);
        if (target.equals(pos) || level.getBlockState(target).equals(carried)) {
            // Without an anchor the block never left the real world, and an identical state needs
            // no write either way.
            return;
        }
        // The recorder hooks LevelChunk#setBlockState, so the state the target loses is kept for
        // the dream layer: the carried block exists in reality yet stays hidden from the dream.
        level.setBlock(target, carried, Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
    }

    /**
     * Puts a creature to sleep instead of killing it; it stays where it stands. The dream-side and
     * the real-side kill share this single call, and it is idempotent: a creature that already
     * sleeps from an earlier intrusion is left alone, so a hit is never processed twice.
     */
    public static void transferLiving(ServerPlayer actor, ServerLevel level, Entity creature) {
        if (creature instanceof Player || !creature.isAlive() || isAsleep(creature)) {
            return;
        }
        creature.setDeltaMovement(Vec3.ZERO);
        creature.setPose(Pose.SLEEPING);
        if (creature instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setPersistenceRequired();
        }
    }

    /** A creature that an earlier intrusion already put to sleep. */
    private static boolean isAsleep(Entity creature) {
        return creature.getPose() == Pose.SLEEPING
                && (!(creature instanceof Mob mob) || mob.isNoAi());
    }

    /** Alias kept for the tool hooks: the creature is moved to sleep, not killed. */
    public static void killEntity(ServerPlayer actor, ServerLevel level, Entity target) {
        transferLiving(actor, level, target);
    }
}
