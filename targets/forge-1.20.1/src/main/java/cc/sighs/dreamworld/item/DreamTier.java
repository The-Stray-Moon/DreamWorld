package cc.sighs.dreamworld.item;

import cc.sighs.dreamworld.DWObjects;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Dream alloy tool material: netherite durability, above-netherite mining speed.
 *
 * <p>Values are intentionally only defined here; tool items read them through {@link Tier}.</p>
 */
public final class DreamTier implements Tier {
    public static final DreamTier INSTANCE = new DreamTier();

    /** Same as netherite. */
    public static final int USES = 2031;
    /** Above netherite (9.0), so the shovel digs faster than diamond tier. */
    public static final float SPEED = 12.0F;
    public static final float ATTACK_DAMAGE_BONUS = 4.0F;
    public static final int ENCHANTMENT_VALUE = 15;
    /** Netherite's harvest level, the highest vanilla tier in 1.20.1. */
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
