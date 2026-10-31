package cc.sighs.dreamworld;

import cc.sighs.dreamworld.config.DreamClientConfig;
import cc.sighs.dreamworld.config.DreamServerConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(DreamWorldNeoForge121.MOD_ID)
public final class DreamWorldNeoForge121 {
    public static final String MOD_ID = "dreamworld";

    public DreamWorldNeoForge121(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, DreamClientConfig.SPEC);
        container.registerConfig(ModConfig.Type.SERVER, DreamServerConfig.SPEC);
        DWRegistry.ITEMS.register(modBus);
        DWRegistry.BLOCKS.register(modBus);
        DWRegistry.BLOCK_ENTITIES.register(modBus);
        DWRegistry.ENTITIES.register(modBus);
        DWRegistry.EFFECTS.register(modBus);
        DWRegistry.PARTICLE_TYPES.register(modBus);
        DWRegistry.TABS.register(modBus);
    }
}
