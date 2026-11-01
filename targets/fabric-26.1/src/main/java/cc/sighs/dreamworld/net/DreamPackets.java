package cc.sighs.dreamworld.net;

import cc.sighs.dreamworld.spi.DreamPacket;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

/**
 * Messages of the mod, independent of the loader's packet representation.
 *
 * <p>Targets encode and decode these records with their own network stack. Shared code only ever
 * constructs and reads the records.</p>
 */
public final class DreamPackets {
    private DreamPackets() {
    }

    /** Tells the owning client that it entered or left the dream world. */
    public record DreamState(boolean dreaming) implements DreamPacket {
    }

    /** Full list of graffiti coordinates plus the server's tunnel limit. */
    public record GraffitiList(List<BlockPos> positions, int maxTunnelsPerGraffiti)
            implements DreamPacket {
    }

    /** One patched position: dream state, hidden real state and whether an intrusion brought it in. */
    public record PatchEntry(long pos, CompoundTag state, CompoundTag realState, boolean intruded) {
    }

    public record DreamPatch(long chunkPos, List<PatchEntry> entries) implements DreamPacket {
    }

    public record DreamEntities(List<Integer> ids) implements DreamPacket {
    }

    public record PetalShape(Identifier entityType, double x, double y, double z, float yRot,
            int count) implements DreamPacket {
    }

    public record BlastParticles(double x, double y, double z, float radius, int count)
            implements DreamPacket {
    }

    /** Which layers currently have a bound anchor. */
    public record AnchorState(boolean realSideBound, boolean dreamSideBound) implements DreamPacket {
    }

    public record Dreamers(List<UUID> dreamers) implements DreamPacket {
    }

    public record TunnelAttach(BlockPos from, BlockPos to) implements DreamPacket {
    }
}
