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
import cc.sighs.dreamworld.item.NeoForgeDreamCrystalItem;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Single source of truth for every registry entry of the mod.
 *
 * <p>Feature code must never create its own {@link DeferredRegister}: add the entry here instead so
 * the registration order and the creative tab stay consistent.</p>
 *
 * <p>26.1 requires an explicit registry key on items and blocks (their properties carry the id), so
 * every entry is created through the deferred helpers that set it.</p>
 */
public final class DWRegistry {
    private DWRegistry() {
    }

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(DreamWorldNeoForge261.MOD_ID);
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(DreamWorldNeoForge261.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, DreamWorldNeoForge261.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, DreamWorldNeoForge261.MOD_ID);
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, DreamWorldNeoForge261.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DreamWorldNeoForge261.MOD_ID);
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, DreamWorldNeoForge261.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, DreamWorldNeoForge261.MOD_ID);

    // ---------------------------------------------------------------- sounds

    /** The blub's own call, the four recordings against the one event. */
    public static final DeferredHolder<SoundEvent, SoundEvent> BLUB_AMBIENT =
            SOUNDS.register("entity.blub.ambient", () -> SoundEvent.createVariableRangeEvent(
                    Identifier.fromNamespaceAndPath(DreamWorldNeoForge261.MOD_ID,
                            "entity.blub.ambient")));

    // ------------------------------------------------------------------ items

    public static final DeferredItem<Item> DREAM_CLAY =
            ITEMS.registerSimpleItem("dream_clay", Item.Properties::new);
    public static final DeferredItem<Item> DREAM_CRYSTAL =
            ITEMS.registerItem("dream_crystal", NeoForgeDreamCrystalItem::new, props -> props);
    public static final DeferredItem<Item> DREAM_POWDER =
            ITEMS.registerSimpleItem("dream_powder", Item.Properties::new);
    public static final DeferredItem<Item> DREAM_ALLOY =
            ITEMS.registerSimpleItem("dream_alloy", Item.Properties::new);

    public static final DeferredItem<DreamTeaItem> DREAM_TEA =
            ITEMS.registerItem("dream_tea", DreamTeaItem::new, props -> props.stacksTo(16)
                    .food(new FoodProperties.Builder().nutrition(0).saturationModifier(0.0F).alwaysEdible()
                            .build())
                    .usingConvertsTo(Items.GLASS_BOTTLE));

    public static final DeferredItem<DreamSwordItem> DREAM_ALLOY_SWORD =
            ITEMS.registerItem("dream_alloy_sword", DreamSwordItem::new,
                    // base 1 + 4 + tier bonus 4 = 9 attack damage
                    props -> props.sword(DreamTier.MATERIAL, 4.0F, -2.4F));
    public static final DeferredItem<DreamShovelItem> DREAM_ALLOY_SHOVEL =
            ITEMS.registerItem("dream_alloy_shovel",
                    props -> new DreamShovelItem(DreamTier.MATERIAL, 1.5F, -3.0F, props),
                    props -> props);
    public static final DeferredItem<DreamWandItem> DREAM_ALLOY_WAND =
            ITEMS.registerItem("dream_alloy_wand", DreamWandItem::new,
                    props -> props.stacksTo(1).durability(DreamTier.USES));
    public static final DeferredItem<DreamSpearItem> DREAM_ALLOY_SPEAR =
            ITEMS.registerItem("dream_alloy_spear", DreamSpearItem::new,
                    props -> props.stacksTo(1).enchantable(1));

    // ----------------------------------------------------------------- blocks

    public static final DeferredBlock<DreamGraffitiBlock> DREAM_GRAFFITI =
            BLOCKS.registerBlock("dream_graffiti", DreamGraffitiBlock::new,
                    () -> BlockBehaviour.Properties.of().strength(0.4F).noOcclusion().noCollision());

    public static final DeferredItem<DreamGraffitiItem> DREAM_GRAFFITI_ITEM =
            ITEMS.registerItem("dream_graffiti",
                    props -> new DreamGraffitiItem(DREAM_GRAFFITI.get(), props),
                    props -> props.useBlockDescriptionPrefix());

    // --------------------------------------------------------- block entities

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DreamGraffitiBlockEntity>> GRAFFITI_BE =
            BLOCK_ENTITIES.register("dream_graffiti",
                    () -> new BlockEntityType<>(DreamGraffitiBlockEntity::new, DREAM_GRAFFITI.get()));

    // --------------------------------------------------------------- entities

    public static final DeferredHolder<EntityType<?>, EntityType<DreamClayEntity>> DREAM_CLAY_ENTITY =
            ENTITIES.register("dream_clay", key -> EntityType.Builder.<DreamClayEntity>of(
                            DreamClayEntity::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F).clientTrackingRange(10).updateInterval(2)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, key)));

    public static final DeferredHolder<EntityType<?>, EntityType<BlubEntity>> BLUB =
            ENTITIES.register("blub", key -> EntityType.Builder.<BlubEntity>of(
                            BlubEntity::new, MobCategory.CREATURE)
                    .sized(0.65F, 0.6F).clientTrackingRange(8)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, key)));

    public static final DeferredHolder<EntityType<?>, EntityType<DreamSpearEntity>> DREAM_SPEAR_ENTITY =
            ENTITIES.register("dream_spear", key -> EntityType.Builder.<DreamSpearEntity>of(
                            DreamSpearEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(10).updateInterval(2)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, key)));

    public static final DeferredItem<SpawnEggItem> BLUB_SPAWN_EGG =
            ITEMS.registerItem("blub_spawn_egg", SpawnEggItem::new,
                    // 26.1 dropped DeferredSpawnEggItem: the egg is a plain item whose entity data
                    // comes from the properties.
                    props -> props.spawnEgg(BLUB.get()));

    // ---------------------------------------------------------------- effects

    public static final DeferredHolder<MobEffect, DreamWalkEffect> DREAM_WALK =
            EFFECTS.register("dream_walk", DreamWalkEffect::new);

    // -------------------------------------------------------------- particles

    /**
     * A cherry petal that honours the velocity it is spawned with.
     *
     * <p>Vanilla {@code CHERRY_LEAVES} ignores the speed arguments and only ever drifts on its own
     * wind, so it cannot be thrown outwards by anything. This type exists so the wand blast can send
     * petals flying away from the centre.</p>
     */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DREAM_PETAL =
            PARTICLE_TYPES.register("dream_petal", () -> new SimpleParticleType(false));

    // ---------------------------------------------------------- creative tab

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> DREAM_TAB =
            TABS.register("dreamworld", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.dreamworld"))
                    .icon(() -> new ItemStack(DREAM_GRAFFITI_ITEM.get()))
                    .displayItems((params, output) -> {
                        output.accept(DREAM_CLAY.get());
                        output.accept(DREAM_CRYSTAL.get());
                        output.accept(DREAM_POWDER.get());
                        output.accept(DREAM_TEA.get());
                        output.accept(DREAM_ALLOY.get());
                        output.accept(DREAM_ALLOY_SWORD.get());
                        output.accept(DREAM_ALLOY_SHOVEL.get());
                        output.accept(DREAM_ALLOY_WAND.get());
                        output.accept(DREAM_ALLOY_SPEAR.get());
                        output.accept(DREAM_GRAFFITI_ITEM.get());
                        output.accept(BLUB_SPAWN_EGG.get());
                    })
                    .build());

    /**
     * Fills the shared object table with this target's registry entries.
     *
     * <p>Deferred holders are bound as suppliers, so the shared tree never asks for an object that
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
