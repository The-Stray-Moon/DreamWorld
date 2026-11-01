package cc.sighs.dreamworld.item;

import cc.sighs.dreamworld.DWObjects;
import cc.sighs.dreamworld.spi.DreamPlatform;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * Dream tea: grants the dream-walk effect, which allows entering the dream world by sleeping.
 *
 * <p>1.20.1 has no {@code Item.Properties.food(...).usingConvertsTo(...)}, so the empty bottle is
 * returned the way a potion does it.</p>
 */
public class DreamTeaItem extends Item {
    public static final int EFFECT_DURATION = 20 * 60 * 10;
    public static final int EFFECT_AMPLIFIER = 0;

    public DreamTeaItem(Item.Properties properties) {
        super(properties);
    }

    /** Tea is drunk from the bottle, so use the drinking animation and sound instead of eating. */
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        Player player = entity instanceof Player ? (Player) entity : null;
        ItemStack result = super.finishUsingItem(stack, level, entity);
        if (!level.isClientSide) {
            DreamPlatform.get().addEffect(entity, DWObjects.DREAM_WALK, EFFECT_DURATION, EFFECT_AMPLIFIER);
        }
        if (player != null) {
            return ItemUtils.createFilledResult(result, player, new ItemStack(Items.GLASS_BOTTLE));
        }
        if (result.isEmpty()) {
            return new ItemStack(Items.GLASS_BOTTLE);
        }
        result = result.copy();
        return result;
    }
}
