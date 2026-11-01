package cc.sighs.dreamworld.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Dream alloy shovel: digs every block type faster than diamond tier.
 *
 * <p>{@link ShovelItem} only answers for the blocks vanilla marks as mineable with a shovel, so both
 * the mining speed and the drop check are widened to every block here. Breaking a block triggers a
 * dream intrusion; see {@link DreamToolEvents}.</p>
 */
public class DreamShovelItem extends ShovelItem {
    public DreamShovelItem(Tier tier, float attackDamageModifier, float attackSpeedModifier,
            Item.Properties properties) {
        super(tier, attackDamageModifier, attackSpeedModifier, properties);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return this.getTier().getSpeed();
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return true;
    }
}
