package cc.sighs.dreamworld.client;

import cc.sighs.dreamworld.boat.DreamTunnelBoat;
import cc.sighs.dreamworld.boat.TunnelGeometry;
import cc.sighs.dreamworld.net.DreamPackets;
import cc.sighs.dreamworld.spi.DreamPlatform;
import com.mojang.blaze3d.platform.InputConstants;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.entity.vehicle.boat.Boat;

/**
 * Keys and clicks of the dream boat.
 *
 * <p>Sprint reverses the boat inside a tunnel; a right click attaches a parked boat to the tunnel it
 * stands next to. Both are client input, so they live here rather than in the boat mixin, which only
 * exposes the two tunnel operations.</p>
 *
 * <p>Input listeners compare against the vanilla bindings instead of polling flags: a vehicle cannot
 * sprint in vanilla, so the sprint flag would never flip. Only the press is handled, so holding a
 * key acts exactly once, and a screen swallows the input - typing in chat must not steer the boat.</p>
 */
public final class DreamBoatControls {
    /** Right mouse button, as GLFW reports it. */
    private static final int RIGHT_BUTTON = 1;

    private DreamBoatControls() {
    }

    /**
     * Right click next to a tunnel attaches the boat to it.
     *
     * <p>Handled on the press itself, before vanilla turns the click into a block interaction: a
     * tunnel normally runs through terrain, so whatever block is in front of the crosshair must not
     * decide whether the tunnel can be reached. The client attaches its own copy first - it is the
     * side that simulates the boat, so a tunnel only the server knows about would be no tunnel at
     * all - and the packet then asks the server to do the same.</p>
     */
    public static void onMouseButton(int button, int action) {
        if (action != InputConstants.PRESS || button != RIGHT_BUTTON) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null) {
            return;
        }
        if (!(minecraft.player.getVehicle() instanceof Boat boat)) {
            return;
        }
        if (!DreamClientState.isDreaming()) {
            return;
        }
        if (!(boat instanceof DreamTunnelBoat tunnelBoat)) {
            return;
        }
        // Aim first: a rider clicking a visible line means that line, not whichever happens to be
        // closer. Without a line under the crosshair the nearest one is taken.
        TunnelGeometry.Pair aimed = aimedPair(minecraft.player);
        if (aimed != null && aimed.equals(tunnelBoat.currentDreamTunnel())) {
            // Clicking the line already being ridden: nothing to switch to.
            return;
        }
        if (!tunnelBoat.attachDreamTunnel(aimed)) {
            // Too far, no tunnel here, or the stretch ahead is too short.
            return;
        }
        TunnelGeometry.Pair attached = tunnelBoat.currentDreamTunnel();
        if (attached != null) {
            DreamPlatform.get().network().sendToServer(
                    new DreamPackets.TunnelAttach(attached.from(), attached.to()));
        }
    }

    /** {@return the tunnel under the crosshair, or {@code null} when none is aimed at} */
    @Nullable
    private static TunnelGeometry.Pair aimedPair(Player player) {
        DreamVisuals.AimedTunnel aimed = DreamVisuals.aimedTunnel(player.getEyePosition(1.0F),
                player.getViewVector(1.0F), DreamVisuals.AIM_REACH, DreamVisuals.AIM_RADIUS);
        return aimed == null ? null
                : new TunnelGeometry.Pair(aimed.tunnel().from(), aimed.tunnel().to());
    }

    public static void onKey(KeyEvent event, int action) {
        if (action != InputConstants.PRESS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null) {
            return;
        }
        if (!minecraft.options.keySprint.matches(event)) {
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
