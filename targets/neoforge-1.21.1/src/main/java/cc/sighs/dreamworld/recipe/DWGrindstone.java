package cc.sighs.dreamworld.recipe;

import cc.sighs.dreamworld.DWRegistry;
import cc.sighs.dreamworld.DreamWorldNeoForge121;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.GrindstoneEvent;

/**
 * Grinding a dream crystal back into dream powder.
 *
 * <p>1.21.1 has no grindstone recipe type - the output is computed inside
 * {@code GrindstoneMenu} - so the only extension point is
 * {@link GrindstoneEvent.OnPlaceItem}. It is fired from the menu logic on both sides with
 * the same slot contents, which keeps client and server results identical. The crystal
 * itself only needs to accept the slot, which {@code DreamCrystalItem} already allows via
 * {@code canGrindstoneRepair}.</p>
 */
@EventBusSubscriber(modid = DreamWorldNeoForge121.MOD_ID)
public final class DWGrindstone {
    /** Small but non-zero reward, matching the tiny amount of vanilla grindstone experience. */
    private static final int GRIND_XP = 1;

    private DWGrindstone() {
    }

    @SubscribeEvent
    public static void onPlaceItem(GrindstoneEvent.OnPlaceItem event) {
        // Only a lone crystal is ground: with something in the second slot the vanilla
        // repair/enchantment behaviour is left untouched, so no second item is consumed.
        if (event.getTopItem().is(DWRegistry.DREAM_CRYSTAL.get()) && event.getBottomItem().isEmpty()) {
            event.setOutput(new ItemStack(DWRegistry.DREAM_POWDER.get()));
            event.setXp(GRIND_XP);
        }
    }
}
