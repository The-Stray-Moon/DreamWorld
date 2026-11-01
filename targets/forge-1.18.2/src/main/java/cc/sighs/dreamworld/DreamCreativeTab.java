package cc.sighs.dreamworld;

import cc.sighs.dreamworld.DWRegistry;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/**
 * The mod's own creative tab.
 *
 * <p>Forge 1.18.2 patches {@link CreativeModeTab} with a {@code (String)} constructor that appends
 * the tab to the page list, so constructing it is the registration. Items join it through
 * {@code Item.Properties#tab}.</p>
 */
public final class DreamCreativeTab extends CreativeModeTab {
    public DreamCreativeTab() {
        super(DreamWorldForge.MOD_ID);
    }

    @Override
    public ItemStack makeIcon() {
        return new ItemStack(DWRegistry.DREAM_GRAFFITI_ITEM.get());
    }
}
