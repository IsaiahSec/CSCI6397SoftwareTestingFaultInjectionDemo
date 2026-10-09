package edu.uca.csci6397.dtrsim;

import java.util.List;
import java.util.Random;

/**
 * Drives the two scenarios against a live {@link NetworkTopology}: the quiet "dozens of
 * isolated failures, automatic recovery" stretch the incident report describes starting
 * around 4 a.m., and the 7:12 a.m. extreme event that triggers the actual outage. Both
 * scenarios run the SAME router code and the SAME {@link RealAlternatePathCalculator} —
 * only the failure pattern this class injects differs.
 */
public final class NetworkSimulator {

    private final NetworkTopology topology;
    private final NetworkObserver observer;
    private final Random eventRandom;

    public NetworkSimulator(NetworkTopology topology, NetworkObserver observer, Random eventRandom) {
        this.topology = topology;
        this.observer = observer;
        this.eventRandom = eventRandom;
    }

    public void startRouters() {
        for (Router router : topology.routers) {
            router.start();
        }
    }

    public void shutdownRouters() throws InterruptedException {
        for (Router router : topology.routers) {
            router.shutdown();
        }
        for (Router router : topology.routers) {
            router.join(1000);
        }
    }

    /**
     * "Starting around 4 a.m. ... unfavorable atmospheric conditions ... began causing
     * frequent and significant microwave link failures across the eastern plains. Over the
     * course of approximately three hours, the network was able to automatically recover
     * from dozens of these failures." One router at a time loses a link, recalculates, and
     * the link comes back — exactly the redundancy the ring topology provides.
     */
    public void runQuietAtmosphericStretch(int flapCount, long downMillis, long settleMillis) throws InterruptedException {
        observer.onLog("4:00 AM-equivalent: unfavorable atmospheric conditions beginning across the eastern plains.");
        for (int i = 1; i <= flapCount; i++) {
            flapOneLink(i, flapCount, downMillis);
            Thread.sleep(settleMillis);
        }
        observer.onLog("Network has automatically recovered from " + flapCount
                + " isolated link failures over the last several hours-equivalent.");
    }

    private void flapOneLink(int index, int total, long downMillis) throws InterruptedException {
        Router router = topology.pickRandomRouter(eventRandom);
        List<String> neighbors = router.neighborIds();
        String neighborId = neighbors.get(eventRandom.nextInt(neighbors.size()));
        observer.onLog(String.format("Link flap %d/%d: %s loses its link to %s.", index, total, router.id, neighborId));
        router.deliver(new NetworkEvent(NetworkEvent.Type.LINK_FAILED, neighborId));
        Thread.sleep(downMillis);
        router.deliver(new NetworkEvent(NetworkEvent.Type.LINK_RESTORED, neighborId));
    }

    /**
     * "At 7:12 a.m. a particularly intense and widespread atmospheric event caused severe
     * simultaneous outages to multiple critical microwave radio links." Every link out of one
     * router fails at once — the simultaneous-failure case the bug can't handle.
     */
    public void triggerExtremeEvent(String routerId) {
        Router router = topology.directory.get(routerId);
        if (router == null) {
            throw new IllegalArgumentException("No such router: " + routerId);
        }
        observer.onLog("7:12 AM-equivalent: a particularly intense atmospheric event causes severe "
                + "simultaneous outages on " + routerId + "'s microwave links.");
        for (String neighborId : router.neighborIds()) {
            router.deliver(new NetworkEvent(NetworkEvent.Type.LINK_FAILED, neighborId));
        }
    }

    /**
     * "By 7:15 a.m. the network was unable to transport traffic, forcing DTR sites statewide
     * into a 'site trunking' condition." True once every router has crashed or is mid-reboot —
     * no router is left in a state where it could carry cross-site traffic.
     */
    public boolean isFullyPartitioned() {
        for (Router router : topology.routers) {
            RouterState state = router.state();
            if (state != RouterState.CRASHED && state != RouterState.REBOOTING) {
                return false;
            }
        }
        return true;
    }

    public int countInState(RouterState state) {
        int count = 0;
        for (Router router : topology.routers) {
            if (router.state() == state) {
                count++;
            }
        }
        return count;
    }

    /**
     * How many routers crashed at any point during this run, regardless of whether they've
     * since recovered. Use this for a run summary instead of {@link #countInState} with
     * {@code CRASHED}/{@code REBOOTING} -- a current-state snapshot silently misses any
     * router that crashed and fully recovered before the snapshot was taken.
     */
    public int countEverCrashed() {
        int count = 0;
        for (Router router : topology.routers) {
            if (router.everCrashed()) {
                count++;
            }
        }
        return count;
    }
}
