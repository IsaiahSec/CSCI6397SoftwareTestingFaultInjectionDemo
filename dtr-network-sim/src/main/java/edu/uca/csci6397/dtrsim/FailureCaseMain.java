package edu.uca.csci6397.dtrsim;

import java.util.Random;

/**
 * Replays the full incident: the same quiet atmospheric stretch as
 * {@link NormalOperationsMain}, then "At 7:12 a.m. a particularly intense and widespread
 * atmospheric event caused severe simultaneous outages to multiple critical microwave
 * radio links. While attempting to calculate alternate paths in response to this extreme
 * event, one router encountered the software bug and crashed. This crash appeared to the
 * network as a site failure and prompted additional path calculation events leading to
 * additional routers encountering the software bug and crashing. As more routers crashed
 * more calculation events were triggered creating a feedback loop that rapidly worsened."
 * By design this run ends the way the report does: "the network was unable to transport
 * traffic, forcing DTR sites statewide into a 'site trunking' condition."
 */
public final class FailureCaseMain {

    public static void main(String[] args) throws Exception {
        System.out.println("=== DTR network simulation: FAILURE CASE ===");
        System.out.println("Replaying the August 6 outage: quiet recovery, then the 7:12 AM-equivalent cascade.\n");

        Random random = new Random(ScenarioConfig.RANDOM_SEED);
        FailureHistory failureHistory = new FailureHistory(ScenarioConfig.FAILURE_WINDOW);
        RealAlternatePathCalculator calculator = new RealAlternatePathCalculator(random);

        DelegatingObserver delegating = new DelegatingObserver();
        NetworkTopology topology = NetworkTopology.build(calculator, failureHistory, delegating,
                ScenarioConfig.REBOOT_MILLIS, ScenarioConfig.CASCADE_DELAY_MILLIS,
                ScenarioConfig.REBOOT_RECOVERY_MILLIS);

        DashboardFactory.Dashboards dashboards = DashboardFactory.start(
                "DTR Network -- Failure Case", ScenarioConfig.WEB_PORT, topology);
        delegating.setDelegate(dashboards.observer);
        NetworkObserver observer = dashboards.observer;

        NetworkSimulator simulator = new NetworkSimulator(topology, observer, random);
        simulator.startRouters();
        observer.onLog("All " + topology.routers.size() + " routers online. Network nominal.");

        simulator.runQuietAtmosphericStretch(ScenarioConfig.QUIET_FLAP_COUNT,
                ScenarioConfig.QUIET_FLAP_DOWN_MILLIS, ScenarioConfig.QUIET_FLAP_SETTLE_MILLIS);

        Thread.sleep(500);
        int crashedBeforeEvent = simulator.countEverCrashed();
        observer.onLog("Quiet stretch complete. Routers that have encountered the bug so far: "
                + crashedBeforeEvent + " / " + topology.routers.size());

        Thread.sleep(500);
        simulator.triggerExtremeEvent(ScenarioConfig.EXTREME_EVENT_ROUTER);

        long deadline = System.currentTimeMillis() + ScenarioConfig.PARTITION_WATCH_MILLIS;
        boolean partitioned = false;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(ScenarioConfig.PARTITION_POLL_MILLIS);
            if (simulator.isFullyPartitioned()) {
                partitioned = true;
                break;
            }
        }

        int crashedOrRebooting = simulator.countInState(RouterState.CRASHED) + simulator.countInState(RouterState.REBOOTING);

        System.out.println();
        System.out.println("=== Summary ===");
        if (partitioned) {
            observer.onLog("7:15 AM-equivalent: every router has crashed or is rebooting. "
                    + "The network can no longer transport cross-site traffic -- site trunking.");
            System.out.println("Full network partition reached: all " + topology.routers.size()
                    + " routers crashed or rebooting.");
            System.out.println("Every site is now limited to 'site trunking' -- local traffic only, exactly as the");
            System.out.println("incident report describes for 7:15 a.m. on August 6.");
        } else {
            System.out.println("Partition not reached within the watch window. Routers down: " + crashedOrRebooting
                    + " / " + topology.routers.size());
        }

        simulator.shutdownRouters();
        if (dashboards.web != null) {
            dashboards.web.stop();
        }
    }

    private FailureCaseMain() {
    }
}
