package edu.uca.csci6397.dtrsim;

/**
 * A message delivered into a router's inbox. Routers are decoupled from each other
 * entirely through these events — a router never calls a method on another router
 * directly, it only ever sends an event into that router's queue. This is what lets
 * each router run on its own thread safely.
 */
public final class NetworkEvent {

    public enum Type {
        /** A microwave link to {@code neighborId} went down (environmental cause, not a bug). */
        LINK_FAILED,
        /** A previously-down link to {@code neighborId} came back up. */
        LINK_RESTORED,
        /**
         * {@code neighborId} crashed. To every other router on the network this looks
         * exactly like a site failure — which is the whole mechanism of the cascade:
         * "This crash appeared to the network as a site failure and prompted additional
         * path calculation events."
         */
        NEIGHBOR_CRASHED,
        /** {@code neighborId} finished rebooting and is back online. */
        NEIGHBOR_RECOVERED,
        /** Tell this router's thread to stop. */
        SHUTDOWN
    }

    public final Type type;
    public final String neighborId;

    public NetworkEvent(Type type, String neighborId) {
        this.type = type;
        this.neighborId = neighborId;
    }

    @Override
    public String toString() {
        return type + (neighborId != null ? "(" + neighborId + ")" : "");
    }
}
