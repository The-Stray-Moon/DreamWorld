package cc.sighs.dreamworld;

import cc.sighs.dreamworld.block.DreamGraffitiBlock;
import cc.sighs.dreamworld.block.DreamGraffitiBlockEntity;
import cc.sighs.dreamworld.effect.DreamWalkEffect;
import cc.sighs.dreamworld.entity.BlubEntity;
import cc.sighs.dreamworld.entity.DreamClayEntity;
import cc.sighs.dreamworld.entity.DreamSpearEntity;
import cc.sighs.dreamworld.item.DreamCrystalItem;
import cc.sighs.dreamworld.item.DreamGraffitiItem;
import cc.sighs.dreamworld.item.DreamShovelItem;
import cc.sighs.dreamworld.item.DreamSpearItem;
import cc.sighs.dreamworld.item.DreamSwordItem;
import cc.sighs.dreamworld.item.DreamTeaItem;
import cc.sighs.dreamworld.item.DreamTier;
import cc.sighs.dreamworld.item.DreamWandItem;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Single source of truth for every registry entry of the mod.
 *
 * <p>Feature code must never create its own {@link DeferredRegister}: add the entry here instead so
 * the registration order and the creative tab stay consistent.</p>
 */
public final class DWRegistry {
    private DWRegistry() {
    }

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, DreamWorld.MOD_ID);
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, DreamWorld.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, DreamWorld.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, DreamWorld.MOD_ID);
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, DreamWorld.MOD_ID);
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, DreamWorld.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, DreamWorld.MOD_ID);

    // ---------------------------------------------------------------- sounds

    /** The blub's own call, the four recordings against the one event. */
    public static final RegistryObject<SoundEvent> BLUB_AMBIENT =
            SOUNDS.register("entity.blub.ambient", () -> new SoundEvent(
                    new ResourceLocation(DreamWorld.MOD_ID, "entity.blub.ambient")));

    // ---------------------------------------------------------- creative tab

    /**
     * 1.19.2 creative tabs are not a registry: a {@link CreativeModeTab} installed itself into
     * {@code CreativeModeTab.TABS} when it was constructed, so this instance is the registration.
     */
    public static final CreativeModeTab DREAM_TAB = new CreativeModeTab("dreamworld") {
        @Override
        public ItemStack makeIcon() {
            return new ItemStack(DREAM_GRAFFITI_ITEM.get());
        }

        @Override
        public void fillItemList(NonNullList<ItemStack> items) {
            items.add(new ItemStack(DREAM_CLAY.get()));
            items.add(new ItemStack(DREAM_CRYSTAL.get()));
            items.add(new ItemStack(DREAM_POWDER.get()));
            items.add(new ItemStack(DREAM_TEA.get()));
            items.add(new ItemStack(DREAM_ALLOY.get()));
            items.add(new ItemStack(DREAM_ALLOY_SWORD.get()));
            items.add(new ItemStack(DREAM_ALLOY_SHOVEL.get()));
            items.add(new ItemStack(DREAM_ALLOY_WAND.get()));
            items.add(new ItemStack(DREAM_ALLOY_SPEAR.get()));
            items.add(new ItemStack(DREAM_GRAFFITI_ITEM.get()));
            items.add(new ItemStack(BLUB_SPAWN_EGG.get()));
        }
    };

    // ------------------------------------------------------------------ items

    public static final RegistryObject<Item> DREAM_CLAY =
            ITEMS.register("dream_clay", () -> new Item(properties()));
    public static final RegistryObject<DreamCrystalItem> DREAM_CRYSTAL =
            ITEMS.register("dream_crystal", () -> new DreamCrystalItem(properties()));
    public static final RegistryObject<Item> DREAM_POWDER =
            ITEMS.register("dream_powder", () -> new Item(properties()));
    public static final RegistryObject<Item> DREAM_ALLOY =
            ITEMS.register("dream_alloy", () -> new Item(properties()));

    public static final RegistryObject<DreamTeaItem> DREAM_TEA =
            ITEMS.register("dream_tea", () -> new DreamTeaItem(properties().stacksTo(16)
                    .craftRemainder(Items.GLASS_BOTTLE)
                    .food(new FoodProperties.Builder().nutrition(0).saturationMod(0.0F)
                            .alwaysEat().build())));

    public static final RegistryObject<DreamSwordItem> DREAM_ALLOY_SWORD =
            ITEMS.register("dream_alloy_sword", () -> new DreamSwordItem(DreamTier.INSTANCE,
                    // base 1 + 4 + tier bonus 4 = 9 attack damage
                    4, -2.4F, properties().durability(DreamTier.USES)));
    public static final RegistryObject<DreamShovelItem> DREAM_ALLOY_SHOVEL =
            ITEMS.register("dream_alloy_shovel", () -> new DreamShovelItem(DreamTier.INSTANCE,
                    1.5F, -3.0F, properties().durability(DreamTier.USES)));
    public static final RegistryObject<DreamWandItem> DREAM_ALLOY_WAND =
            ITEMS.register("dream_alloy_wand", () -> new DreamWandItem(
                    properties().stacksTo(1).durability(DreamTier.USES)));
    public static final RegistryObject<DreamSpearItem> DREAM_ALLOY_SPEAR =
            ITEMS.register("dream_alloy_spear", () -> new DreamSpearItem(properties().stacksTo(1)));

    // ----------------------------------------------------------------- blocks

    public static final RegistryObject<DreamGraffitiBlock> DREAM_GRAFFITI =
            BLOCKS.register("dream_graffiti", () -> new DreamGraffitiBlock(
                    BlockBehaviour.Properties.of(Material.DECORATION).strength(0.4F).noOcclusion()
                            .noCollission()));

    public static final RegistryObject<DreamGraffitiItem> DREAM_GRAFFITI_ITEM =
            ITEMS.register("dream_graffiti", () -> new DreamGraffitiItem(DREAM_GRAFFITI.get(),
                    properties()));

    // --------------------------------------------------------- block entities

    public static final RegistryObject<BlockEntityType<DreamGraffitiBlockEntity>> GRAFFITI_BE =
            BLOCK_ENTITIES.register("dream_graffiti",
                    () -> BlockEntityType.Builder.of(DreamGraffitiBlockEntity::new,
                            DREAM_GRAFFITI.get()).build(null));

    // --------------------------------------------------------------- entities

    public static final RegistryObject<EntityType<DreamClayEntity>> DREAM_CLAY_ENTITY =
            ENTITIES.register("dream_clay", () -> EntityType.Builder.<DreamClayEntity>of(
                            DreamClayEntity::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F).clientTrackingRange(10).updateInterval(2).build("dream_clay"));

    public static final RegistryObject<EntityType<BlubEntity>> BLUB =
            ENTITIES.register("blub", () -> EntityType.Builder.<BlubEntity>of(
                            BlubEntity::new, MobCategory.CREATURE)
                    .sized(0.65F, 0.6F).clientTrackingRange(8).build("blub"));

    public static final RegistryObject<EntityType<DreamSpearEntity>> DREAM_SPEAR_ENTITY =
            ENTITIES.register("dream_spear", () -> EntityType.Builder.<DreamSpearEntity>of(
                            DreamSpearEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(10).updateInterval(2).build("dream_spear"));

    public static final RegistryObject<Item> BLUB_SPAWN_EGG =
            ITEMS.register("blub_spawn_egg", () -> new ForgeSpawnEggItem(BLUB,
                    // Shell and spots, taken from the blub's own cyan-blue palette.
                    0x5CC4EF, 0xDFF6FF, properties()));

    // ---------------------------------------------------------------- effects

    public static final RegistryObject<DreamWalkEffect> DREAM_WALK =
            EFFECTS.register("dream_walk", DreamWalkEffect::new);

    // -------------------------------------------------------------- particles

    /**
     * A cherry petal that honours the velocity it is spawned with.
     *
     * <p>Vanilla 1.19.2 has no cherry leaf particle, so the mod's own petal type is used for
     * weather, the spear trail and the wand burst alike.</p>
     */
    public static final RegistryObject<SimpleParticleType> DREAM_PETAL =
            PARTICLE_TYPES.register("dream_petal", () -> new SimpleParticleType(false));

    private static Item.Properties properties() {
        return new Item.Properties().tab(DREAM_TAB);
    }

    /**
     * Fills the shared object table with this target's registry entries.
     *
     * <p>Registry objects are bound as suppliers, so the shared tree never asks for an object that
     * this loader has not created yet.</p>
     */
    public static void bindObjects() {
        DWObjects.DREAM_CLAY.bind(DREAM_CLAY);
        DWObjects.DREAM_CRYSTAL.bind(DREAM_CRYSTAL);
        DWObjects.DREAM_POWDER.bind(DREAM_POWDER);
        DWObjects.DREAM_ALLOY.bind(DREAM_ALLOY);
        DWObjects.DREAM_TEA.bind(DREAM_TEA);
        DWObjects.DREAM_ALLOY_SWORD.bind(DREAM_ALLOY_SWORD);
        DWObjects.DREAM_ALLOY_SHOVEL.bind(DREAM_ALLOY_SHOVEL);
        DWObjects.DREAM_ALLOY_WAND.bind(DREAM_ALLOY_WAND);
        DWObjects.DREAM_ALLOY_SPEAR.bind(DREAM_ALLOY_SPEAR);
        DWObjects.DREAM_GRAFFITI_ITEM.bind(DREAM_GRAFFITI_ITEM);
        DWObjects.BLUB_SPAWN_EGG.bind(BLUB_SPAWN_EGG);
        DWObjects.DREAM_GRAFFITI.bind(DREAM_GRAFFITI);
        DWObjects.DREAM_GRAFFITI_BLOCK.bind(DREAM_GRAFFITI);
        DWObjects.GRAFFITI_BE.bind(GRAFFITI_BE);
        DWObjects.DREAM_CLAY_ENTITY.bind(DREAM_CLAY_ENTITY);
        DWObjects.BLUB.bind(BLUB);
        DWObjects.DREAM_SPEAR_ENTITY.bind(DREAM_SPEAR_ENTITY);
        DWObjects.DREAM_WALK.bind(DREAM_WALK);
        DWObjects.DREAM_PETAL.bind(DREAM_PETAL);
        DWObjects.BLUB_AMBIENT.bind(BLUB_AMBIENT);
    }
}
