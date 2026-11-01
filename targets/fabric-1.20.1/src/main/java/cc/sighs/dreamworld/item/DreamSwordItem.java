package cc.sighs.dreamworld.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;

/**
 * Dream alloy sword: netherite durability, extra attack damage from the item constructor.
 *
 * <p>1.20.1 carries the tool modifiers on the item itself, so the damage and speed are passed to the
 * vanilla constructor instead of an {@code Item.Properties} attribute component.</p>
 */
public class DreamSwordItem extends SwordItem {
    public static final int ATTACK_DAMAGE_BONUS = 8;
    /** Damage and speed of the 1.21.1 attribute component. */
    public static final int ATTACK_DAMAGE_MODIFIER = 4;
    public static final float ATTACK_SPEED_MODIFIER = -2.4F;

    public DreamSwordItem(Tier tier, Item.Properties properties) {
        super(tier, ATTACK_DAMAGE_MODIFIER, ATTACK_SPEED_MODIFIER, properties);
    }
}
