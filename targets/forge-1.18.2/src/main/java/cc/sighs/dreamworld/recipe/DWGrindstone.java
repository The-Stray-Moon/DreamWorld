package cc.sighs.dreamworld.recipe;

import cc.sighs.dreamworld.DWObjects;
import cc.sighs.dreamworld.DreamWorld;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.GrindstoneEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Grinding a dream crystal back into dream powder.
 *
 * <p>1.18.2 has no grindstone recipe type - the output is computed inside
 * {@code GrindstoneMenu} - so the extension point is {@link GrindstoneEvent.OnPlaceItem}. It is
 * fired from the menu logic on both sides with the same slot contents, which keeps client and
 * server results identical. The crystal itself accepts the slot through
 * {@code Item#canGrindstoneRepair}.</p>
 */
@Mod.EventBusSubscriber(modid = DreamWorld.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DWGrindstone {
    /** Small but non-zero reward, matching the tiny amount of vanilla grindstone experience. */
    private static final int GRIND_XP = 1;

    private DWGrindstone() {
    }

    @SubscribeEvent
    public static void onPlaceItem(GrindstoneEvent.OnPlaceItem event) {
        // Only a lone crystal is ground: with something in the second slot the vanilla
        // repair/enchantment behaviour is left untouched, so no second item is consumed.
        if (event.getTopItem().is(DWObjects.DREAM_CRYSTAL.get()) && event.getBottomItem().isEmpty()) {
            event.setOutput(new ItemStack(DWObjects.DREAM_POWDER.get()));
            event.setXp(GRIND_XP);
        }
    }
}
