package cc.sighs.dreamworld;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(DreamWorldNeoForge121.MOD_ID)
public final class DreamWorldNeoForge121 {
    public static final String MOD_ID = "dreamworld";

    public DreamWorldNeoForge121(IEventBus modBus) {
        DWRegistry.ITEMS.register(modBus);
        DWRegistry.BLOCKS.register(modBus);
        DWRegistry.BLOCK_ENTITIES.register(modBus);
        DWRegistry.ENTITIES.register(modBus);
        DWRegistry.EFFECTS.register(modBus);
        DWRegistry.TABS.register(modBus);
    }
}
