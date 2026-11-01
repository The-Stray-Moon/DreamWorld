package cc.sighs.dreamworld;

import cc.sighs.dreamworld.block.DreamGraffitiBlock;
import cc.sighs.dreamworld.block.DreamGraffitiBlockEntity;
import cc.sighs.dreamworld.effect.DreamWalkEffect;
import cc.sighs.dreamworld.entity.BlubEntity;
import cc.sighs.dreamworld.entity.DreamClayEntity;
import cc.sighs.dreamworld.entity.DreamSpearEntity;
import cc.sighs.dreamworld.spi.DreamHolder;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Every registry entry of the mod, named once for the shared tree.
 *
 * <p>The target's registrar binds each holder to the object it registered. A holder is a live
 * supplier, so code may keep reading the same constant after registration.</p>
 */
public final class DWObjects {
    private DWObjects() {
    }

    // ------------------------------------------------------------------ items
    public static final DreamHolder<Item> DREAM_CLAY = new DreamHolder<>("dream_clay");
    public static final DreamHolder<Item> DREAM_CRYSTAL = new DreamHolder<>("dream_crystal");
    public static final DreamHolder<Item> DREAM_POWDER = new DreamHolder<>("dream_powder");
    public static final DreamHolder<Item> DREAM_ALLOY = new DreamHolder<>("dream_alloy");
    public static final DreamHolder<Item> DREAM_TEA = new DreamHolder<>("dream_tea");
    public static final DreamHolder<Item> DREAM_ALLOY_SWORD = new DreamHolder<>("dream_alloy_sword");
    public static final DreamHolder<Item> DREAM_ALLOY_SHOVEL = new DreamHolder<>("dream_alloy_shovel");
    public static final DreamHolder<Item> DREAM_ALLOY_WAND = new DreamHolder<>("dream_alloy_wand");
    public static final DreamHolder<Item> DREAM_ALLOY_SPEAR = new DreamHolder<>("dream_alloy_spear");
    public static final DreamHolder<Item> DREAM_GRAFFITI_ITEM = new DreamHolder<>("dream_graffiti");
    public static final DreamHolder<Item> BLUB_SPAWN_EGG = new DreamHolder<>("blub_spawn_egg");

    // ----------------------------------------------------------------- blocks
    public static final DreamHolder<DreamGraffitiBlock> DREAM_GRAFFITI =
            new DreamHolder<>("dream_graffiti");
    public static final DreamHolder<BlockEntityType<DreamGraffitiBlockEntity>> GRAFFITI_BE =
            new DreamHolder<>("dream_graffiti");

    /** Kept typed as {@link Block} for callers that only compare block identities. */
    public static final DreamHolder<Block> DREAM_GRAFFITI_BLOCK = new DreamHolder<>("dream_graffiti");

    // --------------------------------------------------------------- entities
    public static final DreamHolder<EntityType<DreamClayEntity>> DREAM_CLAY_ENTITY =
            new DreamHolder<>("dream_clay");
    public static final DreamHolder<EntityType<BlubEntity>> BLUB = new DreamHolder<>("blub");
    public static final DreamHolder<EntityType<DreamSpearEntity>> DREAM_SPEAR_ENTITY =
            new DreamHolder<>("dream_spear");

    // ------------------------------------------------------ effects/particles
    public static final DreamHolder<MobEffect> DREAM_WALK = new DreamHolder<>("dream_walk");
    public static final DreamHolder<SimpleParticleType> DREAM_PETAL =
            new DreamHolder<>("dream_petal");

    // ----------------------------------------------------------------- sounds
    public static final DreamHolder<SoundEvent> BLUB_AMBIENT = new DreamHolder<>("entity.blub.ambient");

    /** The dream walk effect as its concrete type, for code that needs the effect class. */
    @SuppressWarnings("unchecked")
    public static DreamWalkEffect dreamWalkEffect() {
        return (DreamWalkEffect) DREAM_WALK.get();
    }
}
