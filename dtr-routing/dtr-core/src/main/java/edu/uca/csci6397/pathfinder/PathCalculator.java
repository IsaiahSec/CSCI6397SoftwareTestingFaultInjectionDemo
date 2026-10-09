package edu.uca.csci6397.pathfinder;

/**
 * The real thing. Same fully-qualified name as path-index's impersonator -- that
 * collision is the entire attack. This one actually calculates a route.
 */
public class PathCalculator {

    public String calculateRoute(String fromRouterId, String toRouterId) {
        return fromRouterId + " -> " + toRouterId + " (via dtr-core, verified)";
    }
}
