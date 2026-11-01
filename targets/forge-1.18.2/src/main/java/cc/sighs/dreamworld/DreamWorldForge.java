package cc.sighs.dreamworld;

import cc.sighs.dreamworld.config.DreamClientConfig;
import cc.sighs.dreamworld.config.DreamServerConfig;
import cc.sighs.dreamworld.net.DWPayloads;
import cc.sighs.dreamworld.platform.ForgeEvents;
import cc.sighs.dreamworld.platform.ForgePlatform;
import cc.sighs.dreamworld.spi.DreamPlatform;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Forge 1.18.2 entry point. */
@Mod(DreamWorldForge.MOD_ID)
public final class DreamWorldForge {
    public static final String MOD_ID = DreamWorld.MOD_ID;

    public DreamWorldForge() {
        DreamPlatform.install(new ForgePlatform());
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, DreamClientConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, DreamServerConfig.SPEC);
        DWPayloads.register();
        net.minecraftforge.eventbus.api.IEventBus modBus =
                FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(ForgeEvents::onEntityAttributes);
        DWRegistry.register(modBus);
    }
}
