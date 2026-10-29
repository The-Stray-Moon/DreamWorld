package cc.sighs.dreamworld.recipe;

import cc.sighs.dreamworld.DWRegistry;
import cc.sighs.dreamworld.DreamWorldNeoForge121;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;

/**
 * Dream tea brewing: a glass bottle in one of the lower slots plus dream powder in the
 * ingredient slot turns into a dream tea.
 *
 * <p>1.21.1 has no data pack format for brewing, the recipe is only registered through
 * {@link RegisterBrewingRecipesEvent}. The event fires on the main event bus for both the
 * server and the client, so the registration must be deterministic - it is.</p>
 */
@EventBusSubscriber(modid = DreamWorldNeoForge121.MOD_ID)
public final class DWBrewing {
    private DWBrewing() {
    }

    @SubscribeEvent
    public static void registerBrewingRecipes(RegisterBrewingRecipesEvent event) {
        event.getBuilder().addRecipe(
                // Input: sits in the same slot water bottles would use.
                Ingredient.of(Items.GLASS_BOTTLE),
                // Ingredient: sits in the same slot nether wart would use.
                Ingredient.of(DWRegistry.DREAM_POWDER.get()),
                new ItemStack(DWRegistry.DREAM_TEA.get()));
    }
}
