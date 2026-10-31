package cc.sighs.dreamworld.dream;

import cc.sighs.dreamworld.DWRegistry;
import cc.sighs.dreamworld.block.DreamGraffitiBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Dream intrusion, expressed as dream patch edits.
 *
 * <p>The dream layer is the real level plus {@link DreamPatches}. A block broken with dream alloy
 * gear moves to the opposite layer: a dream-side break takes it out of the patch and writes it into
 * the real world, a real-side break takes it out of the real world and writes it into the patch.
 * Both directions carry the state the <em>broken</em> layer showed, never the other side's, and
 * neither drops an item nor spawns experience.</p>
 *
 * <p>A creature hit by the dream alloy sword never dies on either side: damage is cancelled by the
 * tool hook and the mob simply falls asleep where it stands, which is how an intruded creature
 * looks.</p>
 */
public final class DreamIntrusion {
    private DreamIntrusion() {
    }

    /** Carries the block at {@code pos} from the layer the actor stands on into the other one. */
    public static void breakBlock(ServerPlayer actor, ServerLevel level, BlockPos pos) {
        if (DreamManager.isDreaming(actor)) {
            breakDreamBlock(actor, level, pos);
        } else {
            breakRealBlock(level, pos);
        }
    }

    /**
     * Dream-side break: the block leaves the dream layer and is carried into the real world at the
     * intruder's target, overwriting whatever sits there.
     *
     * <p>The carried state is read from the dream layer: reality may already be empty at that
     * position while the dream still shows the block, and following the real state would make the
     * block unbreakable in the dream. Writing the same state back to reality is a no-op of
     * {@code setBlock}, so the "block was never removed from reality" case needs no branch.</p>
     */
    private static void breakDreamBlock(ServerPlayer actor, ServerLevel level, BlockPos pos) {
        BlockState carried = dreamState(level, pos);
        // Graffiti belongs to both sides through its registry, an intrusion must not erase it.
        if (carried.isAir() || carried.getBlock() instanceof DreamGraffitiBlock) {
            return;
        }
        DreamPatches.get(level).setDreamState(level, pos, Blocks.AIR.defaultBlockState());
        BlockPos target = DreamManager.intrusionTarget(actor, pos);
        // The recorder hooks LevelChunk#setBlockState, so the state the target loses is kept for
        // the dream layer: the carried block exists in reality yet stays hidden from the dream.
        level.setBlock(target, carried, Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
    }

    /**
     * Real-side break: the block leaves reality and the dream layer keeps showing it, which is how
     * it "moves" into the dream.
     *
     * <p>The patch has to be written explicitly. {@link DreamPatches#recordRealEdit} only remembers
     * the oldest state of a position, so the recorder alone would keep the air that was stored when
     * the block was originally placed and the broken block would never show up in the dream.</p>
     */
    private static void breakRealBlock(ServerLevel level, BlockPos pos) {
        BlockState carried = level.getBlockState(pos);
        if (carried.isAir() || carried.getBlock() instanceof DreamGraffitiBlock) {
            return;
        }
        DreamPatches.get(level).setDreamState(level, pos, carried);
        level.removeBlock(pos, false);
    }

    /** The state the dream layer shows at {@code pos}: the patch when present, else the real state. */
    private static BlockState dreamState(ServerLevel level, BlockPos pos) {
        return DreamPatches.get(level).forChunkOf(pos).getOrDefault(pos, level.getBlockState(pos));
    }

    /**
     * The state the server should mine at {@code pos} for {@code player}.
     *
     * <p>Vanilla reads the real chunk while digging, but a dreamer sees the patched dream layer and
     * mines dream blocks that usually have no counterpart in reality. Reading the real state would
     * make the server treat the position as air, so no break event would ever fire. This is what
     * {@code ServerPlayerGameMode} reads instead.</p>
     */
    public static BlockState stateForMining(ServerPlayer player, ServerLevel level, BlockPos pos) {
        return DreamManager.isDreaming(player) && isHoldingShovel(player)
                ? dreamState(level, pos)
                : level.getBlockState(pos);
    }

    /**
     * The dream alloy shovel is the only tool that may break blocks on the dream layer.
     *
     * <p>The sword and the wand only ever act on creatures and on the crosshair respectively; letting
     * them break blocks as well would blur the one-tool-one-job rule the gear is built around.</p>
     */
    public static boolean isHoldingShovel(ServerPlayer player) {
        return player.getMainHandItem().is(DWRegistry.DREAM_ALLOY_SHOVEL.get());
    }

    /** The dream alloy sword is the only weapon that may hit creatures on the dream layer. */
    public static boolean isHoldingSword(ServerPlayer player) {
        return player.getMainHandItem().is(DWRegistry.DREAM_ALLOY_SWORD.get());
    }

    /** The wand is the only item whose blast moves blocks across the mirror. */
    public static boolean isHoldingWand(ServerPlayer player) {
        return player.getMainHandItem().is(DWRegistry.DREAM_ALLOY_WAND.get());
    }

    /**
     * Moves a creature to the opposite layer instead of killing it.
     *
     * <p>An intrusion is never a death: the creature leaves the layer it stood on without triggering
     * a death event or loot, and a copy appears on the other layer at the same position (or at the
     * anchor, when one is bound). The copy follows the layer it lands on - asleep and inert in the
     * dream, awake and simulated back in reality - and belongs to that layer only: dream copies are
     * kept away from players that are not dreaming.</p>
     */
    public static void transferLiving(ServerPlayer actor, ServerLevel level, Entity creature) {
        if (creature instanceof Player || !creature.isAlive()) {
            return;
        }
        boolean onDreamSide = DreamEntities.isDreamOnly(creature);
        BlockPos target = DreamManager.intrusionTarget(actor, creature.blockPosition());
        Entity copy = DreamEntities.intrudedCopy(level, creature, target, !onDreamSide);
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
