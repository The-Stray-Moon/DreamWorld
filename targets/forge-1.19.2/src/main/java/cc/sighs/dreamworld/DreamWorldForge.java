package cc.sighs.dreamworld;

import cc.sighs.dreamworld.config.ForgeClientConfig;
import cc.sighs.dreamworld.config.ForgeServerConfig;
import cc.sighs.dreamworld.entity.BlubEntity;
import cc.sighs.dreamworld.net.DWPayloads;
import cc.sighs.dreamworld.platform.ForgePlatform;
import cc.sighs.dreamworld.spi.DreamPlatform;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.brewing.BrewingRecipe;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(DreamWorldForge.MOD_ID)
public final class DreamWorldForge {
    public static final String MOD_ID = DreamWorld.MOD_ID;

    public DreamWorldForge() {
        DreamPlatform.install(new ForgePlatform());

        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ForgeClientConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ForgeServerConfig.SPEC);

        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        DWRegistry.ITEMS.register(modBus);
        DWRegistry.BLOCKS.register(modBus);
        DWRegistry.BLOCK_ENTITIES.register(modBus);
        DWRegistry.ENTITIES.register(modBus);
        DWRegistry.EFFECTS.register(modBus);
        DWRegistry.PARTICLE_TYPES.register(modBus);
        DWRegistry.SOUNDS.register(modBus);
        DWRegistry.bindObjects();

        DWPayloads.register();

        modBus.addListener(this::onEntityAttributes);
        modBus.addListener(this::onCommonSetup);
    }

    private void onEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(DWRegistry.BLUB.get(), BlubEntity.dreamAttributes());
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        // 1.19.2 Forge exposes a brewing registry; the recipe can be added once the items exist.
        event.enqueueWork(() -> BrewingRecipeRegistry.addRecipe(new BrewingRecipe(
                Ingredient.of(Items.GLASS_BOTTLE), Ingredient.of(DWObjects.DREAM_POWDER.get()),
                new ItemStack(DWObjects.DREAM_TEA.get()))));
    }
}
