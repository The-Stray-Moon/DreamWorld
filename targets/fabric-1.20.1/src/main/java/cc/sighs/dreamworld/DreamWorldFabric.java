package cc.sighs.dreamworld;

import cc.sighs.dreamworld.platform.FabricEvents;
import cc.sighs.dreamworld.platform.FabricNetwork;
import cc.sighs.dreamworld.platform.FabricPlatform;
import cc.sighs.dreamworld.spi.DreamPlatform;
import net.fabricmc.api.ModInitializer;

public final class DreamWorldFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        FabricPlatform platform = new FabricPlatform();
        platform.init();
        DreamPlatform.install(platform);

        FabricRegistry.bindObjects();
        FabricNetwork.registerReceivers();
        FabricEvents.register();
    }
}
