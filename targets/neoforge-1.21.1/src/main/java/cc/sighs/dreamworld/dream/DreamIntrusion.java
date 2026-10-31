package cc.sighs.dreamworld.dream;

import cc.sighs.dreamworld.DWRegistry;
import cc.sighs.dreamworld.block.DreamGraffitiBlock;
import cc.sighs.dreamworld.net.DWPayloads;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;

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
    /** Step length of the dream ray cast, in blocks. */
    private static final double RAY_STEP = 0.2D;
    private static final double RAY_EPSILON = 1.0E-6D;
    /** Petals used to draw the silhouette of a transferred creature. */
    private static final int PETAL_SILHOUETTE = 96;
    /** How far the petal silhouette is sent, in blocks. */
    private static final double PETAL_RANGE = 64.0D;

    private DreamIntrusion() {
    }

    /** Carries the block at {@code pos} from the layer the actor stands on into the other one. */
    public static void breakBlock(ServerPlayer actor, ServerLevel level, BlockPos pos) {
        if (DreamManager.isDreaming(actor)) {
            breakDreamBlock(actor, level, pos);
        } else {
            breakRealBlock(actor, level, pos);
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
     *
     * <p>A dream-side anchor redirects the whole transfer: the block leaves reality at {@code pos}
     * and appears in the dream at the anchored position instead of the mirrored coordinates.</p>
     */
    private static void breakRealBlock(ServerPlayer actor, ServerLevel level, BlockPos pos) {
        BlockState carried = level.getBlockState(pos);
        if (carried.isAir() || carried.getBlock() instanceof DreamGraffitiBlock) {
            return;
        }
        BlockPos target = DreamManager.intrusionTarget(actor, pos);
        DreamPatches patches = DreamPatches.get(level);
        patches.setDreamState(level, target, carried);
        level.removeBlock(pos, false);
    }

    /** The state the dream layer shows at {@code pos}: the patch when present, else the real state. */
    private static BlockState dreamState(ServerLevel level, BlockPos pos) {
        return DreamPatches.get(level).stateAt(level, pos);
    }

    /**
     * Ray-casts the dream layer, which the server has no {@code BlockGetter} for.
     *
     * <p>{@code Level#clip} walks the real chunks, so a dreamer aiming at the dream floor would often
     * hit nothing at all and the blast would land far away from the crosshair. The ray is therefore
     * marched along its own length and tested against the state each position shows on the dream
     * side: the patch when there is one, the real block otherwise.</p>
     *
     * @return the first dream block the ray enters, or a miss at {@code to}
     */
    public static BlockHitResult clipDream(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 direction = to.subtract(from);
        double distance = direction.length();
        if (distance < RAY_EPSILON) {
            return BlockHitResult.miss(to, Direction.UP, BlockPos.containing(to));
        }
        int steps = Math.max(1, Mth.ceil(distance / RAY_STEP));
        for (int step = 1; step <= steps; step++) {
            Vec3 point = from.lerp(to, (double) step / steps);
            BlockPos pos = BlockPos.containing(point);
            BlockState state = dreamState(level, pos);
            if (state.isAir()) {
                continue;
            }
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty() || !shape.bounds().move(pos).contains(point)) {
                continue;
            }
            Direction face = Direction.getNearest(-direction.x, -direction.y, -direction.z);
            return new BlockHitResult(point, face, pos, false);
        }
        return BlockHitResult.miss(to, Direction.getNearest(direction.x, direction.y, direction.z),
                BlockPos.containing(to));
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
        // The creature is about to vanish, so the observers of its own layer are told to leave a
        // petal silhouette where it stood. They rebuild it from the model, which only exists on the
        // client side.
        spawnPetalSilhouette(level, creature, onDreamSide);
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

    /** Sends the petal-silhouette request to the players that could see the creature. */
    private static void spawnPetalSilhouette(ServerLevel level, Entity creature, boolean onDreamSide) {
        DWPayloads.PetalShapePayload payload = new DWPayloads.PetalShapePayload(
                BuiltInRegistries.ENTITY_TYPE.getKey(creature.getType()),
                creature.getX(), creature.getY(), creature.getZ(), creature.getYRot(), PETAL_SILHOUETTE);
        double rangeSqr = PETAL_RANGE * PETAL_RANGE;
        for (ServerPlayer nearby : level.players()) {
            if (DreamManager.isDreaming(nearby) != onDreamSide) {
                continue;
            }
            if (nearby.distanceToSqr(creature) <= rangeSqr) {
                PacketDistributor.sendToPlayer(nearby, payload);
            }
        }
    }

    /** Alias kept for the tool hooks: the creature is moved to sleep, not killed. */
    public static void killEntity(ServerPlayer actor, ServerLevel level, Entity target) {
        transferLiving(actor, level, target);
    }
}
