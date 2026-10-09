package edu.uca.csci6397.dtrsim;

/**
 * The undiscovered bug, surfacing. In the real incident this wasn't a clean exception
 * at all — "this bug could cause routers on the network to crash and reboot" — but for
 * the simulation, throwing this from {@link AlternatePathCalculator#calculateAlternatePath}
 * is how a router's path-calculation thread represents "the process just went down."
 */
public final class PathCalculationFault extends RuntimeException {

    public final String routerId;
    public final int recentNetworkFailures;
    public final int simultaneousDownNeighbors;

    public PathCalculationFault(String routerId, int recentNetworkFailures, int simultaneousDownNeighbors) {
        super("unbounded alternate-path expansion on " + routerId + " while handling "
                + simultaneousDownNeighbors + " simultaneous neighbor failure(s), with "
                + recentNetworkFailures + " network-wide failures in the recent window");
        this.routerId = routerId;
        this.recentNetworkFailures = recentNetworkFailures;
        this.simultaneousDownNeighbors = simultaneousDownNeighbors;
    }
}
