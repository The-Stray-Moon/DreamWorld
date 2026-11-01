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
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
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
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Fabric registration; the shared object table is bound to the vanilla registry entries. */
public final class FabricRegistry {
    private FabricRegistry() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(DreamWorld.MOD_ID, path);
    }

    // ------------------------------------------------------------------ items
    public static final Item DREAM_CLAY = registerItem("dream_clay", new Item(new Item.Properties()));
    public static final Item DREAM_CRYSTAL = registerItem("dream_crystal",
            new DreamCrystalItem(new Item.Properties()));
    public static final Item DREAM_POWDER = registerItem("dream_powder", new Item(new Item.Properties()));
    public static final Item DREAM_ALLOY = registerItem("dream_alloy", new Item(new Item.Properties()));

    public static final Item DREAM_TEA = registerItem("dream_tea", new DreamTeaItem(
            new Item.Properties().stacksTo(16).food(new FoodProperties.Builder()
                    .nutrition(0).saturationModifier(0.0F).alwaysEdible()
                    .usingConvertsTo(Items.GLASS_BOTTLE).build())));

    public static final Item DREAM_ALLOY_SWORD = registerItem("dream_alloy_sword",
            new DreamSwordItem(DreamTier.INSTANCE,
                    new Item.Properties().attributes(SwordItem.createAttributes(
                            DreamTier.INSTANCE, 4, -2.4F))));
    public static final Item DREAM_ALLOY_SHOVEL = registerItem("dream_alloy_shovel",
            new DreamShovelItem(DreamTier.INSTANCE,
                    new Item.Properties().attributes(ShovelItem.createAttributes(
                            DreamTier.INSTANCE, 1.5F, -3.0F))));
    public static final Item DREAM_ALLOY_WAND = registerItem("dream_alloy_wand",
            new DreamWandItem(new Item.Properties().stacksTo(1).durability(DreamTier.USES)));
    public static final Item DREAM_ALLOY_SPEAR = registerItem("dream_alloy_spear",
            new DreamSpearItem(new Item.Properties().stacksTo(1)));

    // ----------------------------------------------------------------- blocks
    public static final DreamGraffitiBlock DREAM_GRAFFITI = registerBlock("dream_graffiti",
            new DreamGraffitiBlock(BlockBehaviour.Properties.of().strength(0.4F)
                    .noOcclusion().noCollission()));
    public static final Item DREAM_GRAFFITI_ITEM = registerItem("dream_graffiti",
            new DreamGraffitiItem(DREAM_GRAFFITI, new Item.Properties()));

    public static final BlockEntityType<DreamGraffitiBlockEntity> GRAFFITI_BE =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("dream_graffiti"),
                    BlockEntityType.Builder.of(DreamGraffitiBlockEntity::new, DREAM_GRAFFITI)
                            .build(null));

    // --------------------------------------------------------------- entities
    public static final EntityType<DreamClayEntity> DREAM_CLAY_ENTITY =
            registerEntity("dream_clay", EntityType.Builder.<DreamClayEntity>of(
                            DreamClayEntity::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F).clientTrackingRange(10).updateInterval(2));
    public static final EntityType<BlubEntity> BLUB =
            registerEntity("blub", EntityType.Builder.<BlubEntity>of(
                            BlubEntity::new, MobCategory.CREATURE)
                    .sized(0.65F, 0.6F).clientTrackingRange(8));
    public static final EntityType<DreamSpearEntity> DREAM_SPEAR_ENTITY =
            registerEntity("dream_spear", EntityType.Builder.<DreamSpearEntity>of(
                            DreamSpearEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(10).updateInterval(2));

    public static final Item BLUB_SPAWN_EGG = registerItem("blub_spawn_egg",
            new SpawnEggItem(BLUB, 0x5CC4EF, 0xDFF6FF, new Item.Properties()));

    // ---------------------------------------------------------------- effects
    public static final MobEffect DREAM_WALK = Registry.register(BuiltInRegistries.MOB_EFFECT,
            id("dream_walk"), new DreamWalkEffect());

    // -------------------------------------------------------------- particles
    public static final SimpleParticleType DREAM_PETAL = Registry.register(
            BuiltInRegistries.PARTICLE_TYPE, id("dream_petal"),
            new cc.sighs.dreamworld.registry.FabricSimpleParticleType(false));

    // ----------------------------------------------------------------- sounds
    public static final SoundEvent BLUB_AMBIENT = Registry.register(BuiltInRegistries.SOUND_EVENT,
            id("entity.blub.ambient"), SoundEvent.createVariableRangeEvent(id("entity.blub.ambient")));

    // ---------------------------------------------------------- creative tab
    public static final CreativeModeTab DREAM_TAB = Registry.register(
            BuiltInRegistries.CREATIVE_MODE_TAB, id("dreamworld"),
            FabricItemGroup.builder()
                    .title(Component.translatable("itemGroup.dreamworld"))
                    .icon(() -> new ItemStack(DREAM_GRAFFITI_ITEM))
                    .displayItems((params, output) -> {
                        output.accept(DREAM_CLAY);
                        output.accept(DREAM_CRYSTAL);
                        output.accept(DREAM_POWDER);
                        output.accept(DREAM_TEA);
                        output.accept(DREAM_ALLOY);
                        output.accept(DREAM_ALLOY_SWORD);
                        output.accept(DREAM_ALLOY_SHOVEL);
                        output.accept(DREAM_ALLOY_WAND);
                        output.accept(DREAM_ALLOY_SPEAR);
                        output.accept(DREAM_GRAFFITI_ITEM);
                        output.accept(BLUB_SPAWN_EGG);
                    })
                    .build());

    /** Fills the shared object table once every entry exists. */
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

    private static Item registerItem(String path, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, id(path), item);
    }

    private static DreamGraffitiBlock registerBlock(String path, DreamGraffitiBlock block) {
        Registry.register(BuiltInRegistries.BLOCK, id(path), block);
        return block;
    }

    private static <T extends net.minecraft.world.entity.Entity> EntityType<T> registerEntity(
            String path, EntityType.Builder<T> builder) {
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, id(path), builder.build(path));
    }
}
