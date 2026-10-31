package cc.sighs.dreamworld.boat;

/**
 * A boat that can ride a graffiti tunnel.
 *
 * <p>The tunnel state lives in the boat mixin, and the client has to drive one of its parts from
 * outside a mixin - the sprint key is only readable from client code - so the mixin exposes exactly
 * those two bits: is the boat on a tunnel, and turn it around.</p>
 */
public interface DreamTunnelBoat {
    /** {@return whether the boat is currently locked onto a graffiti tunnel} */
    boolean inDreamTunnel();

    /**
     * {@return the graffiti pair of the tunnel being ridden, or {@code null} when not in one}
     *
     * <p>Used to tell the tunnel under the crosshair apart from the one already being ridden.</p>
     */
    @javax.annotation.Nullable
    TunnelGeometry.Pair currentDreamTunnel();

    /**
     * Attaches the boat to a tunnel within reach, or switches it to another one.
     *
     * @param preferred the tunnel the rider pointed at, or {@code null} for the nearest one; the
     *                  line already being ridden is never picked again
     * @return whether the boat is now riding the requested tunnel
     */
    boolean attachDreamTunnel(@javax.annotation.Nullable TunnelGeometry.Pair preferred);

    /** Reverses the travel direction along the tunnel the boat rides. */
    void reverseDreamTunnel();
}
