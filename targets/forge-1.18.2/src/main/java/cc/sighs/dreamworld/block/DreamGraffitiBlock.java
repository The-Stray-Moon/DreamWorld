package cc.sighs.dreamworld.block;

import cc.sighs.dreamworld.DWObjects;
import cc.sighs.dreamworld.dream.GraffitiRegistry;
import cc.sighs.dreamworld.dream.GraffitiSync;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Dream graffiti: a flat block entity that can be attached to any face and exists in both
 * the dream and the real side at the same coordinates. Every placed graffiti is registered with a
 * UUID in {@link GraffitiRegistry}; pairs of them form rideable tunnels.
 *
 * <p>1.18.2 has no {@code codec()} on blocks and declares the overridable shape/placement methods
 * public, so the overrides are widened accordingly.</p>
 */
public class DreamGraffitiBlock extends BaseEntityBlock {
    /** Face the graffiti is attached to (points from the graffiti into the support block). */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    /** Thin plate hugging the support face, matching the 0.8 pixel decal model. */
    private static final Map<Direction, VoxelShape> SHAPES = createShapes();

    public DreamGraffitiBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    private static Map<Direction, VoxelShape> createShapes() {
        Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        shapes.put(Direction.DOWN, Block.box(0.0D, 0.0D, 0.0D, 16.0D, 0.8D, 16.0D));
        shapes.put(Direction.UP, Block.box(0.0D, 15.2D, 0.0D, 16.0D, 16.0D, 16.0D));
        shapes.put(Direction.NORTH, Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 0.8D));
        shapes.put(Direction.SOUTH, Block.box(0.0D, 0.0D, 15.2D, 16.0D, 16.0D, 16.0D));
        shapes.put(Direction.WEST, Block.box(0.0D, 0.0D, 0.0D, 0.8D, 16.0D, 16.0D));
        shapes.put(Direction.EAST, Block.box(15.2D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D));
        return shapes;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState clickedFaceFirst = defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
        if (clickedFaceFirst.canSurvive(context.getLevel(), context.getClickedPos())) {
            return clickedFaceFirst;
        }
        for (Direction direction : context.getNearestLookingDirections()) {
            BlockState state = defaultBlockState().setValue(FACING, direction.getOpposite());
            if (state.canSurvive(context.getLevel(), context.getClickedPos())) {
                return state;
            }
        }
        return null;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return canSurvive(level, pos, state.getValue(FACING));
    }

    /** True when the block the graffiti faces is sturdy enough to hold it. */
    public static boolean canSurvive(LevelReader level, BlockPos pos, Direction facing) {
        BlockPos support = pos.relative(facing);
        return level.getBlockState(support).isFaceSturdy(level, support, facing.getOpposite());
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.canSurvive(level, pos)
                ? super.updateShape(state, direction, neighborState, level, pos, neighborPos)
                : Blocks.AIR.defaultBlockState();
    }

    /**
     * Vanilla shape updates would silently erase the graffiti; dropping the item first keeps the
     * graffiti from being lost when its support disappears.
     */
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
            BlockPos neighborPos, boolean movedByPiston) {
        if (!level.isClientSide && level.getBlockState(pos).is(this)
                && !canSurvive(level, pos, state.getValue(FACING))) {
            dropResources(state, level, pos);
            level.removeBlock(pos, false);
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof DreamGraffitiBlockEntity graffiti
                && graffiti.ensureRegistered(serverLevel)) {
            GraffitiSync.broadcast(serverLevel);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
            boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            if (level.getBlockEntity(pos) instanceof DreamGraffitiBlockEntity graffiti
                    && graffiti.graffitiId() != null) {
                GraffitiRegistry.remove(level, graffiti.graffitiId());
                GraffitiSync.broadcast(serverLevel);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DreamGraffitiBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, DWObjects.GRAFFITI_BE.get(),
                        DreamGraffitiBlockEntity::serverTick);
    }
}
