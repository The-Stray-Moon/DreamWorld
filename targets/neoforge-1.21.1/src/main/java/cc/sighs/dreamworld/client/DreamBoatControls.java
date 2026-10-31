package cc.sighs.dreamworld.client;

import cc.sighs.dreamworld.DreamWorldNeoForge121;
import cc.sighs.dreamworld.boat.DreamTunnelBoat;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.vehicle.Boat;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

/**
 * Sprint key inside a graffiti tunnel: turn the boat around.
 *
 * <p>Listens to the key event itself instead of polling the key every tick, and compares against the
 * sprint binding rather than the player's sprint flag - a vehicle cannot sprint in vanilla, so the
 * flag would never flip. Only the press is handled, so holding the key turns the boat exactly once.</p>
 *
 * <p>A screen swallows the press: typing in chat must not turn the boat.</p>
 */
@EventBusSubscriber(modid = DreamWorldNeoForge121.MOD_ID, value = Dist.CLIENT)
public final class DreamBoatControls {
    private DreamBoatControls() {
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getAction() != InputConstants.PRESS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null) {
            return;
        }
        if (!minecraft.options.keySprint.matches(event.getKey(), event.getScanCode())) {
            return;
        }
        if (minecraft.player == null || !(minecraft.player.getVehicle() instanceof Boat boat)) {
            return;
        }
        if (boat instanceof DreamTunnelBoat tunnelBoat && tunnelBoat.inDreamTunnel()) {
            tunnelBoat.reverseDreamTunnel();
        }
    }
}
