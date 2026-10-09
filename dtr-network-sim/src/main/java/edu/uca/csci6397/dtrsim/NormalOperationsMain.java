package edu.uca.csci6397.dtrsim;

import java.util.Random;

/**
 * Replays only the quiet part of the incident report: "Starting around 4 a.m. on August 6,
 * unfavorable atmospheric conditions ... began causing frequent and significant microwave
 * link failures across the eastern plains. Over the course of approximately three hours,
 * the network was able to automatically recover from dozens of these failures." No bug,
 * no crash, no cascade — just the network's normal redundancy doing its job, over and over.
 */
public final class NormalOperationsMain {

    public static void main(String[] args) throws Exception {
        System.out.println("=== DTR network simulation: NORMAL OPERATIONS ===");
        System.out.println("Replaying the quiet, self-healing stretch before anything went wrong.\n");

        Random random = new Random(ScenarioConfig.RANDOM_SEED);
        FailureHistory failureHistory = new FailureHistory(ScenarioConfig.FAILURE_WINDOW);
        RealAlternatePathCalculator calculator = new RealAlternatePathCalculator(random);

        DelegatingObserver delegating = new DelegatingObserver();
        NetworkTopology topology = NetworkTopology.build(calculator, failureHistory, delegating,
                ScenarioConfig.REBOOT_MILLIS, ScenarioConfig.CASCADE_DELAY_MILLIS,
                ScenarioConfig.REBOOT_RECOVERY_MILLIS);

        DashboardFactory.Dashboards dashboards = DashboardFactory.start(
                "DTR Network -- Normal Operations", ScenarioConfig.WEB_PORT, topology);
        delegating.setDelegate(dashboards.observer);

        NetworkSimulator simulator = new NetworkSimulator(topology, dashboards.observer, random);
        simulator.startRouters();
        dashboards.observer.onLog("All " + topology.routers.size() + " routers online. Network nominal.");

        simulator.runQuietAtmosphericStretch(ScenarioConfig.QUIET_FLAP_COUNT,
                ScenarioConfig.QUIET_FLAP_DOWN_MILLIS, ScenarioConfig.QUIET_FLAP_SETTLE_MILLIS);

        Thread.sleep(500);
        int crashed = simulator.countEverCrashed();

        System.out.println();
        System.out.println("=== Summary ===");
        System.out.println(ScenarioConfig.QUIET_FLAP_COUNT + " isolated link failures were injected and automatically recovered.");
        System.out.println("Routers that crashed: " + crashed + " / " + topology.routers.size());
        if (crashed == 0) {
            System.out.println("No router ever encountered the alternate-path bug -- this is exactly what Aviat's own");
            System.out.println("smaller-scale testing saw, and exactly why the bug shipped undetected.");
        }

        simulator.shutdownRouters();
        if (dashboards.web != null) {
            dashboards.web.stop();
        }
    }

    private NormalOperationsMain() {
    }
}
