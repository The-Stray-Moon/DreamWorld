package cc.sighs.dreamworld;

import cc.sighs.dreamworld.net.DWPayloads;
import cc.sighs.dreamworld.platform.FabricEvents;
import cc.sighs.dreamworld.platform.FabricNetwork;
import cc.sighs.dreamworld.platform.FabricPlatform;
import cc.sighs.dreamworld.spi.DreamPlatform;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class DreamWorldFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        FabricPlatform platform = new FabricPlatform();
        platform.init();
        DreamPlatform.install(platform);

        FabricNetwork.registerTypes();
        FabricRegistry.bindObjects();
        FabricEvents.register();
        ServerPlayNetworking.registerGlobalReceiver(DWPayloads.TunnelAttachPayload.TYPE,
                FabricNetwork::handleTunnelAttach);
    }
}
