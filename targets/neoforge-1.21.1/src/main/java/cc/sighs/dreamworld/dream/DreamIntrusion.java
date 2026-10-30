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
        if (level.getBlockState(target).equals(carried)) {
            // Nothing to move: the destination already holds the carried block.
            return;
        }
        // The recorder hooks LevelChunk#setBlockState, so the state the target loses is kept for
        // the dream layer: the carried block exists in reality yet stays hidden from the dream.
        level.setBlock(target, carried, Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
    }

    /**
     * Moves a creature to the opposite layer instead of killing it.
     *
     * <p>An intrusion is never a death: the creature leaves the layer it stood on without triggering
     * a death event or loot, and a sleeping copy appears on the other layer at the same position (or
     * at the anchor, when one is bound). The copy belongs to that layer only: dream copies are kept
     * away from players that are not dreaming.</p>
     */
    public static void transferLiving(ServerPlayer actor, ServerLevel level, Entity creature) {
        if (creature instanceof Player || !creature.isAlive()) {
            return;
        }
        boolean onDreamSide = DreamEntities.isDreamOnly(creature);
        BlockPos target = DreamManager.intrusionTarget(actor, creature.blockPosition());
        Entity copy = DreamEntities.sleepingCopy(level, creature, target, !onDreamSide);
        if (copy != null) {
            if (onDreamSide) {
                // A dream creature moving to reality leaves clay on the dream side.
                DreamEntities.leaveDream(creature);
            }
            creature.discard();
        }
        DreamEntities.syncAll(level);
    }

    /** Alias kept for the tool hooks: the creature is moved to sleep, not killed. */
    public static void killEntity(ServerPlayer actor, ServerLevel level, Entity target) {
        transferLiving(actor, level, target);
    }
}
