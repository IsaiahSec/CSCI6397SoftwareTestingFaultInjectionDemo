package edu.uca.csci6397.dtrsim;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Builds the demo network: a ring of sites (the microwave backhaul's normal redundancy
 * — every site has two neighbors to fail over to) plus a couple of long cross-links, the
 * way a real regional backhaul isn't a clean circle. This redundancy is exactly why the
 * real network could shrug off "dozens" of isolated failures: losing one link still
 * leaves an alternate path through the ring.
 */
public final class NetworkTopology {

    /** Eastern-plains-flavored site names; order also defines ring adjacency. */
    private static final String[] SITE_NAMES = {
            "Prairie Ridge", "Antelope Junction", "Silver Mesa", "North Summit",
            "Eastbend", "Stonecrop", "Windrow", "Cedar Hollow"
    };

    // Long cross-links added on top of the ring, by site index.
    private static final int[][] CROSS_LINKS = {
            {0, 4},
            {2, 6}
    };

    public final List<Router> routers;
    public final Map<String, Router> directory;

    private NetworkTopology(List<Router> routers, Map<String, Router> directory) {
        this.routers = routers;
        this.directory = directory;
    }

    public static NetworkTopology build(AlternatePathCalculator calculator, FailureHistory failureHistory,
                                         NetworkObserver observer, long rebootMillis) {
        return build(calculator, failureHistory, observer, rebootMillis, 0L, Math.max(1, rebootMillis / 2));
    }

    public static NetworkTopology build(AlternatePathCalculator calculator, FailureHistory failureHistory,
                                         NetworkObserver observer, long rebootMillis, long cascadeDelayMillis) {
        return build(calculator, failureHistory, observer, rebootMillis, cascadeDelayMillis,
                Math.max(1, rebootMillis / 2));
    }

    public static NetworkTopology build(AlternatePathCalculator calculator, FailureHistory failureHistory,
                                         NetworkObserver observer, long rebootMillis, long cascadeDelayMillis,
                                         long rebootRecoveryMillis) {
        int n = SITE_NAMES.length;
        Map<String, Router> directory = new LinkedHashMap<>();
        Map<String, java.util.List<String>> adjacency = new LinkedHashMap<>();

        for (int i = 0; i < n; i++) {
            String id = "R" + (i + 1);
            adjacency.put(id, new java.util.ArrayList<>());
        }
        // Ring edges.
        for (int i = 0; i < n; i++) {
            String a = "R" + (i + 1);
            String b = "R" + (((i + 1) % n) + 1);
            adjacency.get(a).add(b);
            adjacency.get(b).add(a);
        }
        // Cross-links.
        for (int[] link : CROSS_LINKS) {
            String a = "R" + (link[0] + 1);
            String b = "R" + (link[1] + 1);
            adjacency.get(a).add(b);
            adjacency.get(b).add(a);
        }

        List<Router> routers = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            String id = "R" + (i + 1);
            Router router = new Router(id, SITE_NAMES[i], adjacency.get(id), directory,
                    calculator, failureHistory, observer, rebootMillis, cascadeDelayMillis, rebootRecoveryMillis);
            directory.put(id, router);
            routers.add(router);
        }
        return new NetworkTopology(routers, directory);
    }

    public Router pickRandomRouter(Random random) {
        return routers.get(random.nextInt(routers.size()));
    }
}
