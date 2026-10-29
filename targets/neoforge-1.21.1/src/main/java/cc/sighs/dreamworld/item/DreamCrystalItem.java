package cc.sighs.dreamworld.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Dream crystal: smelted from dream clay, ground into dream powder on a grindstone,
 * and part of the dream alloy recipe.
 */
public class DreamCrystalItem extends Item {
    public DreamCrystalItem(Item.Properties properties) {
        super(properties);
    }

    /** Lets the crystal sit in a grindstone slot at all; the actual recipe is event driven. */
    @Override
    public boolean canGrindstoneRepair(ItemStack stack) {
        return true;
    }
}
