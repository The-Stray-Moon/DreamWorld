package cc.sighs.dreamworld.platform;

import cc.sighs.dreamworld.DreamWorld;
import cc.sighs.dreamworld.spi.DreamPersistentData;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/** Fabric keeps the mod-scoped compound in a persistent data attachment. */
public final class FabricPersistentData implements DreamPersistentData {
    private static final AttachmentType<CompoundTag> DATA = AttachmentRegistry.createPersistent(
            ResourceLocation.fromNamespaceAndPath(DreamWorld.MOD_ID, "data"), CompoundTag.CODEC);

    @Override
    public CompoundTag get(Entity entity) {
        return ((AttachmentTarget) entity).getAttachedOrCreate(DATA, CompoundTag::new);
    }
}
