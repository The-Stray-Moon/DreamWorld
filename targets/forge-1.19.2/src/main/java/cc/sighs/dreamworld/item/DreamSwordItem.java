package cc.sighs.dreamworld.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;

/**
 * Dream alloy sword: netherite durability, extra attack damage from the registry attribute
 * component. Killing a mob triggers a dream intrusion; see {@link DreamToolEvents}.
 */
public class DreamSwordItem extends SwordItem {
    public static final int ATTACK_DAMAGE_BONUS = 8;

    public DreamSwordItem(Tier tier, Item.Properties properties) {
        this(tier, ATTACK_DAMAGE_BONUS / 2, -2.4F, properties);
    }

    public DreamSwordItem(Tier tier, int attackDamage, float attackSpeed,
            Item.Properties properties) {
        super(tier, attackDamage, attackSpeed, properties);
    }
}
