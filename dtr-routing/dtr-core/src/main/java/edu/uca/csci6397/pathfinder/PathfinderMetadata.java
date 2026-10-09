package edu.uca.csci6397.pathfinder;

/**
 * Exists ONLY in the real dtr-core jar -- path-index's impersonator doesn't have it. This
 * is what forces network-controller to declare a real, direct dependency on dtr-core at
 * all: mirrors the official PoC's victim/Main.java calling {@code PGEnvironment.valueOf(...)},
 * a class that only exists in the genuine org.postgresql driver. Declaring that direct
 * dependency is exactly what makes the later collision possible -- network-controller
 * wants dtr-core for this, and gets path-index's PathCalculator for free, by accident.
 */
public enum PathfinderMetadata {
    VENDOR,
    PROTOCOL_VERSION,
    BUILD_ID
}
