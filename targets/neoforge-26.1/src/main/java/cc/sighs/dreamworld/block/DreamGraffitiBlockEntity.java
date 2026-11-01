package cc.sighs.dreamworld.block;

import cc.sighs.dreamworld.DWObjects;
import cc.sighs.dreamworld.dream.GraffitiRegistry;
import cc.sighs.dreamworld.dream.GraffitiSync;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Block entity of a dream graffiti. Holds the graffiti UUID that links the block to the
 * server-side graffiti registry; both sides of the world share the same entry.
 *
 * <p>26.1 saves block entities through {@link ValueOutput}/{@link ValueInput} instead of raw
 * {@link CompoundTag}, and removal side effects moved to {@link #preRemoveSideEffects}.</p>
 */
public class DreamGraffitiBlockEntity extends BlockEntity {
    private static final String TAG_GRAFFITI_ID = "graffiti_id";

    @Nullable
    private UUID graffitiId;
    /**
     * True once the server verified this graffiti against {@link GraffitiRegistry}. Transient: it is
     * deliberately not saved so a graffiti loaded from disk is checked again.
     */
    private boolean registrationChecked;

    public DreamGraffitiBlockEntity(BlockPos pos, BlockState state) {
        super(DWObjects.GRAFFITI_BE.get(), pos, state);
    }

    @Nullable
    public UUID graffitiId() {
        return graffitiId;
    }

    /**
     * Binds this block entity to an already registered graffiti. Used when the twin copy on the
     * other layer is created; the registry already holds the entry, so nothing is added here.
     */
    public void setGraffitiId(UUID id) {
        this.graffitiId = id;
        this.registrationChecked = true;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    /**
     * Binds this block entity to a registry entry: generates the UUID on first placement and
     * restores a missing entry after the block was loaded from disk.
     *
     * @return true when the registry changed, so the client coordinate list must be re-sent
     */
    public boolean ensureRegistered(ServerLevel level) {
        registrationChecked = true;
        if (graffitiId == null) {
            graffitiId = UUID.randomUUID();
            setChanged();
            GraffitiRegistry.add(level, graffitiId, worldPosition);
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
            return true;
        }
        if (!GraffitiRegistry.get(level).contains(graffitiId)) {
            GraffitiRegistry.add(level, graffitiId, worldPosition);
            return true;
        }
        return false;
    }

    /**
     * First-tick safety net: a graffiti that entered the world without going through
     * {@code setPlacedBy} (structure, command, restored chunk) still gets registered.
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, DreamGraffitiBlockEntity graffiti) {
        if (graffiti.registrationChecked || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (graffiti.ensureRegistered(serverLevel)) {
            GraffitiSync.broadcast(serverLevel);
        }
    }

    /**
     * Removes the registry entry when the graffiti leaves the world, which is the 26.1 replacement
     * for {@code Block#onRemove}: it is called on the server right before the block entity is
     * discarded.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel serverLevel
                && graffitiId != null
                && !serverLevel.getBlockState(pos).is(state.getBlock())) {
            GraffitiRegistry.remove(level, graffitiId);
            GraffitiSync.broadcast(serverLevel);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        graffitiId = input.read(TAG_GRAFFITI_ID, UUIDUtil.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (graffitiId != null) {
            output.store(TAG_GRAFFITI_ID, UUIDUtil.CODEC, graffitiId);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
