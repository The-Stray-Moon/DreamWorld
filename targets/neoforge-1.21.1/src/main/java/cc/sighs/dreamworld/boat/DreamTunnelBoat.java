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

    /** Reverses the travel direction along the tunnel the boat rides. */
    void reverseDreamTunnel();
}
