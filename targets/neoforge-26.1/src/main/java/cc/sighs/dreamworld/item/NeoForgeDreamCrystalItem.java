package cc.sighs.dreamworld.item;

import net.minecraft.world.item.ItemStack;

/** NeoForge adds the grindstone-slot hook to {@code Item}; the crystal opts in there. */
public class NeoForgeDreamCrystalItem extends DreamCrystalItem {
    public NeoForgeDreamCrystalItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canGrindstoneRepair(ItemStack stack) {
        return true;
    }
}
