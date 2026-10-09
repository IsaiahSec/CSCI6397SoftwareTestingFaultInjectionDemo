package edu.uca.csci6397.dtrsim;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The production implementation — and the carrier of the bug.
 *
 * <p>This models the August 2025 Colorado DTR outage's root cause as described in the
 * incident report: "a previously undiscovered bug existed in part of the software that
 * calculates alternate paths. This bug could cause routers on the network to crash and
 * reboot while performing these calculations. The chance of a crash was small, but
 * increased with the number and frequency of link and site failures on the network."
 *
 * <p>That sentence is encoded directly in {@link #crashProbability}: a small base rate,
 * scaled up by both the network-wide recent-failure count (squared, not linear — this is
 * what turns a handful of crashes into the report's "feedback loop that rapidly worsened"
 * instead of a flat, constant risk) and the number of simultaneous failures this one
 * calculation has to handle. Neither input is hidden global state — both arrive as
 * parameters — so the exact same crash condition that takes down the live simulation can
 * be handed to this class directly in a unit test (see fault_injection.sh), without
 * needing a running network at all.
 *
 * <p>This is also, not incidentally, the reason a small test network with light,
 * infrequent simulated failures — the report's explanation for why Aviat's own testing
 * never caught this — would almost never push {@code recentNetworkFailures} or
 * {@code downNeighborIds.size()} high enough for {@code crashProbability} to matter.
 */
public final class RealAlternatePathCalculator implements AlternatePathCalculator {

    private static final double BASE_CRASH_PROBABILITY = 0.005;
    private static final double FAILURE_FREQUENCY_SCALING = 0.02;
    private static final double SIMULTANEOUS_FAILURE_SCALING = 0.35;
    private static final double MAX_CRASH_PROBABILITY = 0.95;

    private final Random random;

    public RealAlternatePathCalculator(Random random) {
        this.random = random;
    }

    double crashProbability(int recentNetworkFailures, int simultaneousDownNeighbors) {
        double p = BASE_CRASH_PROBABILITY
                + FAILURE_FREQUENCY_SCALING * recentNetworkFailures * recentNetworkFailures
                + SIMULTANEOUS_FAILURE_SCALING * Math.max(0, simultaneousDownNeighbors - 1);
        return Math.min(MAX_CRASH_PROBABILITY, p);
    }

    @Override
    public List<String> calculateAlternatePath(String routerId, List<String> downNeighborIds, int recentNetworkFailures)
            throws PathCalculationFault {
        int simultaneous = downNeighborIds.size();
        double p = crashProbability(recentNetworkFailures, simultaneous);

        if (random.nextDouble() < p) {
            throw new PathCalculationFault(routerId, recentNetworkFailures, simultaneous);
        }

        List<String> alternates = new ArrayList<>();
        for (String down : downNeighborIds) {
            alternates.add(down + " ~via~ " + routerId + "-backup-link");
        }
        return alternates;
    }
}
