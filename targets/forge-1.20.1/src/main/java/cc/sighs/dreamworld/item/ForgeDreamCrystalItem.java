package cc.sighs.dreamworld.item;

import net.minecraft.world.item.ItemStack;

/** Forge adds the grindstone-slot hook to {@code Item}; the crystal opts in there. */
public class ForgeDreamCrystalItem extends DreamCrystalItem {
    public ForgeDreamCrystalItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canGrindstoneRepair(ItemStack stack) {
        return true;
    }
}
