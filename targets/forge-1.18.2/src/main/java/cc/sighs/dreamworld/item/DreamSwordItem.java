package cc.sighs.dreamworld.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;

/**
 * Dream alloy sword: netherite durability, extra attack damage. Killing a mob triggers a dream
 * intrusion; see {@link DreamToolEvents}.
 *
 * <p>1.18.2 takes the attack damage and speed as constructor arguments instead of an attribute
 * component: 4 + tier bonus 4 + base 1 = 9 attack damage.</p>
 */
public class DreamSwordItem extends SwordItem {
    public static final int ATTACK_DAMAGE_BONUS = 8;

    public DreamSwordItem(Tier tier, int attackDamage, float attackSpeed, Item.Properties properties) {
        super(tier, attackDamage, attackSpeed, properties);
    }
}
