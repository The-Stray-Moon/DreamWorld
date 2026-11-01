package cc.sighs.dreamworld;

import cc.sighs.dreamworld.config.DreamClientConfig;
import cc.sighs.dreamworld.config.DreamServerConfig;
import cc.sighs.dreamworld.platform.ForgeClientEvents;
import cc.sighs.dreamworld.platform.ForgeEvents;
import cc.sighs.dreamworld.platform.ForgePlatform;
import cc.sighs.dreamworld.spi.DreamPlatform;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(DreamWorldForge.MOD_ID)
public final class DreamWorldForge {
    public static final String MOD_ID = DreamWorld.MOD_ID;

    public DreamWorldForge() {
        DreamPlatform.install(new ForgePlatform());
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, DreamClientConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, DreamServerConfig.SPEC);

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        DWRegistry.registerAll(modBus);
        DWRegistry.bindObjects();
        modBus.addListener(ForgeEvents::onCommonSetup);
        modBus.addListener(ForgeEvents::onEntityAttributes);
        if (FMLEnvironment.dist.isClient()) {
            ForgeClientEvents.registerModBus(modBus);
        }
    }
}
