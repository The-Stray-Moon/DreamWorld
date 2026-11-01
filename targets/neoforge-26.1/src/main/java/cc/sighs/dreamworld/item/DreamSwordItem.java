package cc.sighs.dreamworld.item;

import net.minecraft.world.item.Item;

/**
 * Dream alloy sword: netherite durability, extra attack damage from the attribute component.
 * Killing a mob triggers a dream intrusion; see {@link DreamToolEvents}.
 *
 * <p>26.1 no longer has a {@code SwordItem}: the sword behaviour (tool rules, weapon component,
 * attributes) is folded into {@link Item.Properties#sword}.</p>
 */
public class DreamSwordItem extends Item {
    public static final int ATTACK_DAMAGE_BONUS = 8;

    public DreamSwordItem(Item.Properties properties) {
        super(properties);
    }
}
