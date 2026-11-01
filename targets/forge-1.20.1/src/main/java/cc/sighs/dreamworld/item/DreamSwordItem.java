package cc.sighs.dreamworld.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;

/**
 * Dream alloy sword: netherite durability, extra attack damage from the constructor, which is how
 * 1.20.1 carries tool attributes. Killing a mob triggers a dream intrusion; see
 * {@link DreamToolEvents}.
 */
public class DreamSwordItem extends SwordItem {
    public static final int ATTACK_DAMAGE_BONUS = 8;

    public DreamSwordItem(Tier tier, int attackDamageModifier, float attackSpeedModifier,
            Item.Properties properties) {
        super(tier, attackDamageModifier, attackSpeedModifier, properties);
    }
}
