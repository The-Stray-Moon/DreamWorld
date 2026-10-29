package cc.sighs.dreamworld.item;

import cc.sighs.dreamworld.DWRegistry;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * Dream tea: grants the dream-walk effect, which allows entering the dream world by sleeping.
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
        ItemStack result = super.finishUsingItem(stack, level, entity);
        if (!level.isClientSide) {
            entity.addEffect(new MobEffectInstance(DWRegistry.DREAM_WALK, EFFECT_DURATION, EFFECT_AMPLIFIER));
        }
        return result;
    }
}
