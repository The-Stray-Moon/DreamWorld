package cc.sighs.dreamworld.item;

import cc.sighs.dreamworld.DreamWorld;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

/**
 * Dream alloy tool material: netherite durability, above-netherite mining speed.
 *
 * <p>26.1 replaced the {@code Tier} interface with the {@link ToolMaterial} record, so the values
 * live in one shared record instance. Repair is the dream alloy item itself, expressed as the
 * {@code dreamworld:dream_alloy_repair} item tag.</p>
 */
public final class DreamTier {
    /** Same as netherite. */
    public static final int USES = 2031;
    /** Above netherite (9.0), so the shovel digs faster than diamond tier. */
    public static final float SPEED = 12.0F;
    public static final float ATTACK_DAMAGE_BONUS = 4.0F;
    public static final int ENCHANTMENT_VALUE = 15;

    /** Items that repair the tool set: the dream alloy itself. */
    public static final TagKey<Item> REPAIR_ITEMS = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(DreamWorld.MOD_ID, "dream_alloy_repair"));

    public static final ToolMaterial MATERIAL = new ToolMaterial(
            BlockTags.INCORRECT_FOR_NETHERITE_TOOL, USES, SPEED, ATTACK_DAMAGE_BONUS,
            ENCHANTMENT_VALUE, REPAIR_ITEMS);

    private DreamTier() {
    }
}
