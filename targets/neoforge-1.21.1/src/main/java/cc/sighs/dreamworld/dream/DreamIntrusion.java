package cc.sighs.dreamworld.dream;

import cc.sighs.dreamworld.block.DreamGraffitiBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Dream intrusion, expressed as dream patch edits.
 *
 * <p>The dream layer is the real level plus {@link DreamPatches}. When a block changes in the real
 * world, the recorder keeps the old state in the patch, so the dream still shows it: the block
 * "moved" to the dream side without ever being duplicated. Breaking a block while dreaming does the
 * opposite: only the dream patch changes, the real world is untouched.</p>
 *
 * <p>A creature hit by the dream alloy sword never dies: damage is cancelled by the tool hook and
 * the mob simply falls asleep where it stands, which is how an intruded creature looks.</p>
 */
public final class DreamIntrusion {
    private DreamIntrusion() {
    }

    /** Applies a dream-side break to the patch set; called only for dreaming players. */
    public static void breakBlock(ServerPlayer actor, ServerLevel level, BlockPos pos) {
        BlockState carried = level.getBlockState(pos);
        if (carried.isAir()) {
            return;
        }
        // Graffiti belongs to both sides through its registry, an intrusion must not erase it.
        if (carried.getBlock() instanceof DreamGraffitiBlock) {
            return;
        }
        DreamPatches.get(level).setDreamState(level, pos, Blocks.AIR.defaultBlockState());
    }

    /** Puts a creature to sleep instead of killing it; it stays where it stands. */
    public static void transferLiving(ServerPlayer actor, ServerLevel level, Entity creature) {
        if (creature instanceof Player || !creature.isAlive()) {
            return;
        }
        creature.setDeltaMovement(Vec3.ZERO);
        creature.setPose(Pose.SLEEPING);
        if (creature instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setPersistenceRequired();
        }
    }

    /** Alias kept for the tool hooks: the creature is moved to sleep, not killed. */
    public static void killEntity(ServerPlayer actor, ServerLevel level, Entity target) {
        transferLiving(actor, level, target);
    }
}
