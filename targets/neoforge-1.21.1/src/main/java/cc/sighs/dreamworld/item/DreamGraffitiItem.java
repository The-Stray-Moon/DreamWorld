package cc.sighs.dreamworld.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

/**
 * Dream graffiti item: places a {@link cc.sighs.dreamworld.block.DreamGraffitiBlock}.
 * Graffiti is the only block that can be placed and used inside the dream world.
 */
public class DreamGraffitiItem extends BlockItem {
    public DreamGraffitiItem(Block block, Properties properties) {
        super(block, properties);
    }
}
