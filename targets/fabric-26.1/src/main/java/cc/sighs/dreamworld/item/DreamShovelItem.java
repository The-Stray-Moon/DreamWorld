package cc.sighs.dreamworld.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Dream alloy shovel: digs every block type faster than diamond tier.
 *
 * <p>{@link ShovelItem} only covers {@code mineable/shovel} through its tool component, so both the
 * mining speed and the drop check are widened to every block here. Breaking a block triggers a
 * dream intrusion; see {@link DreamToolEvents}.</p>
 */
public class DreamShovelItem extends ShovelItem {
    public DreamShovelItem(ToolMaterial material, float attackDamageBaseline,
            float attackSpeedBaseline, Item.Properties properties) {
        super(material, attackDamageBaseline, attackSpeedBaseline, properties);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return DreamTier.SPEED;
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return true;
    }
}
