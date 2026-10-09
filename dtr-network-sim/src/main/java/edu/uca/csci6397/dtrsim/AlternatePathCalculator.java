package edu.uca.csci6397.dtrsim;

import java.util.List;

/**
 * The boundary (slide 4/6 pattern, applied here): the one thing a {@link Router}
 * depends on to compute a route around a failed neighbor. Production code
 * ({@link Router}) depends on this interface, not on {@link RealAlternatePathCalculator}
 * directly — which is exactly what makes this swappable for a test double in
 * fault_injection.sh, and exactly the design choice the real Aviat Networks codebase
 * evidently didn't make (or didn't test through) before shipping.
 *
 * @param routerId the router performing the calculation
 * @param downNeighborIds the neighbor(s) this router currently cannot reach
 * @param recentNetworkFailures the network-wide failure count in the recent window
 *        (from {@link FailureHistory}) — passed in explicitly, rather than read from
 *        shared state inside the implementation, specifically so a test can supply any
 *        value it wants without needing a real, populated {@link FailureHistory}.
 * @return the alternate route(s) found, one description per down neighbor
 * @throws PathCalculationFault if the calculation fails — the undiscovered bug
 */
public interface AlternatePathCalculator {
    List<String> calculateAlternatePath(String routerId, List<String> downNeighborIds, int recentNetworkFailures)
            throws PathCalculationFault;
}
