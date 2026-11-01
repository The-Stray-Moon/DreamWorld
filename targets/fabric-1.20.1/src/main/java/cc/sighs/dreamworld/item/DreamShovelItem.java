package cc.sighs.dreamworld.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Dream alloy shovel: digs every block type faster than diamond tier.
 *
 * <p>{@link ShovelItem} only covers {@code mineable/shovel} through its tool tier, so both the mining
 * speed and the drop check are widened to every block here. Breaking a block triggers a dream
 * intrusion; see {@link DreamToolEvents}.</p>
 */
public class DreamShovelItem extends ShovelItem {
    /** Damage and speed of the 1.21.1 attribute component. */
    public static final float ATTACK_DAMAGE_MODIFIER = 1.5F;
    public static final float ATTACK_SPEED_MODIFIER = -3.0F;

    public DreamShovelItem(Tier tier, Item.Properties properties) {
        super(tier, ATTACK_DAMAGE_MODIFIER, ATTACK_SPEED_MODIFIER, properties);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return this.getTier().getSpeed();
    }

    @Override
    public boolean isCorrectToolForDrops(BlockState state) {
        return true;
    }
}
