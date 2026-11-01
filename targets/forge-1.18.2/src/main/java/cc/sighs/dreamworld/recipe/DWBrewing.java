package cc.sighs.dreamworld.recipe;

import cc.sighs.dreamworld.DWObjects;
import cc.sighs.dreamworld.DreamWorld;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Dream tea brewing: a glass bottle in one of the lower slots plus dream powder in the ingredient
 * slot turns into a dream tea.
 *
 * <p>1.18.2 has no data pack format for brewing and no brewing-recipe event, but Forge does expose
 * {@link BrewingRecipeRegistry}. The registry is only filled after the item registry event, so the
 * recipe is added from common setup, where {@code DWObjects} already points at real items.</p>
 */
@Mod.EventBusSubscriber(modid = DreamWorld.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DWBrewing {
    private DWBrewing() {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> BrewingRecipeRegistry.addRecipe(
                // Input: sits in the same slot water bottles would use.
                Ingredient.of(Items.GLASS_BOTTLE),
                // Ingredient: sits in the same slot nether wart would use.
                Ingredient.of(DWObjects.DREAM_POWDER.get()),
                new ItemStack(DWObjects.DREAM_TEA.get())));
    }
}
