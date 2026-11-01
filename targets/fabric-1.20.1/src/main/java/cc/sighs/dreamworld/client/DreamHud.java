package cc.sighs.dreamworld.client;

import cc.sighs.dreamworld.DWObjects;
import cc.sighs.dreamworld.boat.DreamTunnelBoat;
import cc.sighs.dreamworld.boat.TunnelGeometry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Action-bar hints of the dream world.
 *
 * <p>The bar carries reversing and leaving a graffiti tunnel, what the crosshair is pointing at when
 * it points at one, and the way to clear an anchor that blocks a second spear. The hints are joined
 * into one line and sent through {@link Player#displayClientMessage}; an unchanged line is refreshed
 * before vanilla's 60-tick overlay timer runs out, and an empty hint clears the bar.</p>
 */
public final class DreamHud {
    /** How far the crosshair reaches when looking for a tunnel to point at. */
    private static final double TUNNEL_AIM_REACH = DreamVisuals.AIM_REACH;
    /** How far off the line the crosshair may be and still count as pointing at a tunnel. */
    private static final double TUNNEL_AIM_RADIUS = DreamVisuals.AIM_RADIUS;
    /** Ticks between two sends of an unchanged line; vanilla hides it after 60 ticks. */
    private static final int REFRESH_TICKS = 40;

    /** The last line sent to the action bar, so an unchanged hint is not re-sent every tick. */
    private static String lastHint = "";
    private static int ticksSinceHint;

    private DreamHud() {
    }

    /** Sends or refreshes the action-bar hint for this client tick. */
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) {
            reset();
            return;
        }
        List<Component> hints = hints(player);
        if (hints.isEmpty()) {
            clear(player);
            return;
        }
        Component line = join(hints);
        if (!line.getString().equals(lastHint) || ++ticksSinceHint >= REFRESH_TICKS) {
            player.displayClientMessage(line, true);
            lastHint = line.getString();
            ticksSinceHint = 0;
        }
    }

    /** Drops the remembered line, for example when the client leaves the world. */
    public static void reset() {
        lastHint = "";
        ticksSinceHint = 0;
    }

    private static void clear(Player player) {
        if (lastHint.isEmpty()) {
            return;
        }
        player.displayClientMessage(Component.empty(), true);
        reset();
    }

    private static List<Component> hints(Player player) {
        List<Component> hints = new ArrayList<>(4);
        if (isOnTunnel(player)) {
            hints.add(Component.translatable("hud.dreamworld.reverse_tunnel"));
            hints.add(Component.translatable("hud.dreamworld.leave_tunnel"));
        }
        Component aimHint = aimHint(player);
        if (aimHint != null) {
            hints.add(aimHint);
        }
        if (holdsSpear(player) && DreamClientState.isAnchorBound(DreamClientState.isDreaming())) {
            hints.add(Component.translatable("hud.dreamworld.destroy_anchor"));
        }
        return hints;
    }

    /** Joins the hints into the single line an action bar can show. */
    private static Component join(List<Component> hints) {
        MutableComponent line = Component.empty();
        for (int index = 0; index < hints.size(); index++) {
            if (index > 0) {
                line.append(Component.literal("  "));
            }
            line.append(hints.get(index));
        }
        return line;
    }

    /**
     * The hint for a tunnel under the crosshair.
     *
     * <p>The crosshair has to actually point at a tunnel segment; then the hint says what is missing:
     * the boat, the distance, or nothing at all. Blocks in between do not matter - a tunnel runs
     * through terrain, so aiming through a wall is the normal case.</p>
     *
     * @return the hint, or {@code null} when the crosshair is on no usable tunnel
     */
    private static Component aimHint(Player player) {
        if (!DreamClientState.isDreaming() || DreamVisuals.tunnels().isEmpty()) {
            return null;
        }
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 direction = player.getViewVector(1.0F);
        DreamVisuals.AimedTunnel aimed = DreamVisuals.aimedTunnel(eye, direction, TUNNEL_AIM_REACH,
                TUNNEL_AIM_RADIUS);
        if (aimed == null) {
            return null;
        }
        if (!(player.getVehicle() instanceof Boat boat)) {
            return Component.translatable("hud.dreamworld.tunnel_need_boat");
        }
        if (boat instanceof DreamTunnelBoat tunnelBoat && isRiding(tunnelBoat, aimed.tunnel())) {
            // Pointing at the line under the hull: nothing to switch to.
            return null;
        }
        Vec3 from = Vec3.atCenterOf(aimed.tunnel().from());
        Vec3 to = Vec3.atCenterOf(aimed.tunnel().to());
        if (distanceToSegment(boat.position(), from, to) > reach(player)) {
            return Component.translatable("hud.dreamworld.tunnel_too_far");
        }
        return Component.translatable("hud.dreamworld.tunnel_enter");
    }

    /** {@return how far the player can reach a tunnel: interaction range plus one block} */
    private static double reach(Player player) {
        // 1.20.1 has no block interaction range attribute; the 1.21.1 default is 4.5 blocks.
        return 4.5D + 1.0D;
    }

    /** {@return whether the boat already rides the tunnel under the crosshair} */
    private static boolean isRiding(DreamTunnelBoat tunnelBoat, DreamVisuals.TunnelEdge aimed) {
        TunnelGeometry.Pair riding = tunnelBoat.currentDreamTunnel();
        if (riding == null) {
            return false;
        }
        boolean same = riding.from().equals(aimed.from()) && riding.to().equals(aimed.to());
        boolean flipped = riding.from().equals(aimed.to()) && riding.to().equals(aimed.from());
        return same || flipped;
    }

    /** Distance from a point to a segment; the boat may sit anywhere along one. */
    private static double distanceToSegment(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 segment = end.subtract(start);
        double lengthSqr = segment.lengthSqr();
        if (lengthSqr < 1.0E-6D) {
            return point.distanceTo(start);
        }
        double t = Mth.clamp(point.subtract(start).dot(segment) / lengthSqr, 0.0D, 1.0D);
        return point.distanceTo(start.add(segment.scale(t)));
    }

    /** {@return whether the boat is actually riding a tunnel} */
    private static boolean isOnTunnel(Player player) {
        return player.getVehicle() instanceof DreamTunnelBoat tunnelBoat && tunnelBoat.inDreamTunnel();
    }

    /** {@return whether a dream alloy spear is held in either hand} */
    private static boolean holdsSpear(Player player) {
        return isSpear(player.getMainHandItem()) || isSpear(player.getOffhandItem());
    }

    private static boolean isSpear(ItemStack stack) {
        return stack.is(DWObjects.DREAM_ALLOY_SPEAR.get());
    }
}
