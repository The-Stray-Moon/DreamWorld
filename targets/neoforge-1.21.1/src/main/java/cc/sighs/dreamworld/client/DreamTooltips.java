package cc.sighs.dreamworld.client;

import cc.sighs.dreamworld.DWRegistry;
import cc.sighs.dreamworld.DreamWorldNeoForge121;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Hover descriptions of every item of the mod.
 *
 * <p>Kept in one place so the wording stays consistent and the plain items registered in
 * {@link DWRegistry} do not need a subclass just for a tooltip. Only what an item does is listed
 * here: the recipe chain is already visible in the recipe book, and repeating it on both the input
 * and the output only makes the tooltip longer.</p>
 */
@EventBusSubscriber(modid = DreamWorldNeoForge121.MOD_ID, value = Dist.CLIENT)
public final class DreamTooltips {
    private static final ChatFormatting COLOR = ChatFormatting.GRAY;

    private DreamTooltips() {
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        List<Component> tooltip = event.getToolTip();
        if (stack.is(DWRegistry.DREAM_CLAY.get())) {
            add(tooltip, "tooltip.dreamworld.dream_clay.1");
        } else if (stack.is(DWRegistry.DREAM_CRYSTAL.get())) {
            add(tooltip, "tooltip.dreamworld.dream_crystal.1");
        } else if (stack.is(DWRegistry.DREAM_POWDER.get())) {
            add(tooltip, "tooltip.dreamworld.dream_powder.1");
        } else if (stack.is(DWRegistry.DREAM_TEA.get())) {
            add(tooltip,
                    "tooltip.dreamworld.dream_tea.1",
                    "tooltip.dreamworld.dream_tea.2");
        } else if (stack.is(DWRegistry.DREAM_ALLOY.get())) {
            add(tooltip, "tooltip.dreamworld.dream_alloy.1");
        } else if (stack.is(DWRegistry.DREAM_ALLOY_SWORD.get())) {
            add(tooltip,
                    "tooltip.dreamworld.dream_alloy_sword.1",
                    "tooltip.dreamworld.dream_alloy_sword.2");
        } else if (stack.is(DWRegistry.DREAM_ALLOY_SHOVEL.get())) {
            add(tooltip,
                    "tooltip.dreamworld.dream_alloy_shovel.1",
                    "tooltip.dreamworld.dream_alloy_shovel.2");
        } else if (stack.is(DWRegistry.DREAM_ALLOY_WAND.get())) {
            add(tooltip,
                    "tooltip.dreamworld.dream_alloy_wand.1",
                    "tooltip.dreamworld.dream_alloy_wand.2",
                    "tooltip.dreamworld.dream_alloy_wand.3");
        } else if (stack.is(DWRegistry.DREAM_ALLOY_SPEAR.get())) {
            add(tooltip,
                    "tooltip.dreamworld.dream_alloy_spear.1",
                    "tooltip.dreamworld.dream_alloy_spear.2",
                    "tooltip.dreamworld.dream_alloy_spear.3");
        } else if (stack.is(DWRegistry.DREAM_GRAFFITI_ITEM.get())) {
            add(tooltip,
                    "tooltip.dreamworld.dream_graffiti.1",
                    "tooltip.dreamworld.dream_graffiti.2");
        } else if (stack.is(DWRegistry.BLUB_SPAWN_EGG.get())) {
            add(tooltip, "tooltip.dreamworld.blub_spawn_egg.1");
        }
    }

    private static void add(List<Component> tooltip, String... keys) {
        for (String key : keys) {
            tooltip.add(Component.translatable(key).withStyle(COLOR));
        }
    }
}
