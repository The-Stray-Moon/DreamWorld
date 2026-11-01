package cc.sighs.dreamworld.client;

import cc.sighs.dreamworld.DWObjects;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.item.ItemStack;

/**
 * Hover descriptions of every item of the mod.
 *
 * <p>Kept in one place so the wording stays consistent and the plain items registered by the target
 * registrar do not need a subclass just for a tooltip. Only what an item does is listed here: the
 * recipe chain is already visible in the recipe book, and repeating it on both the input and the
 * output only makes the tooltip longer.</p>
 */
public final class DreamTooltips {
    private static final ChatFormatting COLOR = ChatFormatting.GRAY;

    private DreamTooltips() {
    }

    public static void append(ItemStack stack, List<Component> tooltip) {
        if (stack.is(DWObjects.DREAM_CLAY.get())) {
            add(tooltip, "tooltip.dreamworld.dream_clay.1");
        } else if (stack.is(DWObjects.DREAM_CRYSTAL.get())) {
            add(tooltip, "tooltip.dreamworld.dream_crystal.1");
        } else if (stack.is(DWObjects.DREAM_POWDER.get())) {
            add(tooltip, "tooltip.dreamworld.dream_powder.1");
        } else if (stack.is(DWObjects.DREAM_TEA.get())) {
            add(tooltip,
                    "tooltip.dreamworld.dream_tea.1",
                    "tooltip.dreamworld.dream_tea.2");
        } else if (stack.is(DWObjects.DREAM_ALLOY.get())) {
            add(tooltip, "tooltip.dreamworld.dream_alloy.1");
        } else if (stack.is(DWObjects.DREAM_ALLOY_SWORD.get())) {
            add(tooltip,
                    "tooltip.dreamworld.dream_alloy_sword.1",
                    "tooltip.dreamworld.dream_alloy_sword.2");
        } else if (stack.is(DWObjects.DREAM_ALLOY_SHOVEL.get())) {
            add(tooltip,
                    "tooltip.dreamworld.dream_alloy_shovel.1",
                    "tooltip.dreamworld.dream_alloy_shovel.2");
        } else if (stack.is(DWObjects.DREAM_ALLOY_WAND.get())) {
            add(tooltip,
                    "tooltip.dreamworld.dream_alloy_wand.1",
                    "tooltip.dreamworld.dream_alloy_wand.2",
                    "tooltip.dreamworld.dream_alloy_wand.3");
        } else if (stack.is(DWObjects.DREAM_ALLOY_SPEAR.get())) {
            add(tooltip,
                    "tooltip.dreamworld.dream_alloy_spear.1",
                    "tooltip.dreamworld.dream_alloy_spear.2",
                    "tooltip.dreamworld.dream_alloy_spear.3");
        } else if (stack.is(DWObjects.DREAM_GRAFFITI_ITEM.get())) {
            add(tooltip,
                    "tooltip.dreamworld.dream_graffiti.1",
                    "tooltip.dreamworld.dream_graffiti.2");
        } else if (stack.is(DWObjects.BLUB_SPAWN_EGG.get())) {
            add(tooltip, "tooltip.dreamworld.blub_spawn_egg.1");
        }
    }

    private static void add(List<Component> tooltip, String... keys) {
        for (String key : keys) {
            tooltip.add(new TranslatableComponent(key).withStyle(COLOR));
        }
    }
}
