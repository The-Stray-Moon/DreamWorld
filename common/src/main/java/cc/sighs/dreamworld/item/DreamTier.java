package cc.sighs.dreamworld.item;

import cc.sighs.dreamworld.DWObjects;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.block.Block;

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
    public TagKey<Block> getIncorrectBlocksForDrops() {
        return BlockTags.INCORRECT_FOR_NETHERITE_TOOL;
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
