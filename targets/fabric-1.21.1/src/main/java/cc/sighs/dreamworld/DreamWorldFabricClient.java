package cc.sighs.dreamworld;

import cc.sighs.dreamworld.platform.FabricClientEvents;
import net.fabricmc.api.ClientModInitializer;

public final class DreamWorldFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricClientEvents.register();
    }
}
