package cc.sighs.dreamworld.item;

import cc.sighs.dreamworld.DWObjects;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Dream alloy tool material: netherite durability, above-netherite mining speed.
 *
 * <p>1.18.2 tiers are level based ({@code getLevel}), and the tool tag is only consulted by
 * Forge's tier sorting. The shovel overrides the drop check anyway, so the level only has to be
 * at least netherite's.</p>
 */
public final class DreamTier implements Tier {
    public static final DreamTier INSTANCE = new DreamTier();

    /** Same as netherite. */
    public static final int USES = 2031;
    /** Above netherite (9.0), so the shovel digs faster than diamond tier. */
    public static final float SPEED = 12.0F;
    public static final float ATTACK_DAMAGE_BONUS = 4.0F;
    public static final int ENCHANTMENT_VALUE = 15;
    /** Netherite tier level. */
    public static final int LEVEL = 4;

    private DreamTier() {
    }

    @Override
    public int getUses() {
        return USES;
    }

    @Override
    public float getSpeed() {
        return SPEED;
    }

    @Override
    public float getAttackDamageBonus() {
        return ATTACK_DAMAGE_BONUS;
    }

    @Override
    public int getLevel() {
        return LEVEL;
    }

    @Override
    public int getEnchantmentValue() {
        return ENCHANTMENT_VALUE;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.of(DWObjects.DREAM_ALLOY.get());
    }
}
