package edu.uca.csci6397.dtrsim;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A single DTR site's router. Each router owns its own {@link Thread} and its own
 * inbox — that's what "the health of each can be represented visually and
 * independently" means mechanically: a router is never blocked waiting on another
 * router, so the dashboard shows every site's state changing concurrently and
 * asynchronously, the way a real distributed network actually behaves.
 *
 * <p>Routers only ever talk to each other through {@link NetworkEvent}s dropped into
 * one another's queues ({@link #deliver}) — never a direct method call across threads.
 *
 * <p>{@link #handleEvent} is the actual state machine and is intentionally callable on
 * its own, synchronously, with no thread involved — that's the method fault_injection.sh
 * exercises directly with test doubles standing in for {@link #calculator}.
 */
public final class Router {

    public final String id;
    public final String siteName;
    private final List<String> neighborIds;
    private final Map<String, Router> directory;
    private final Set<String> downNeighbors = new HashSet<>();

    private final AlternatePathCalculator calculator;
    private final FailureHistory failureHistory;
    private final NetworkObserver observer;
    private final long rebootMillis;
    private final long cascadeDelayMillis;
    private final long rebootRecoveryMillis;

    private volatile RouterState state = RouterState.ONLINE;
    private volatile boolean everCrashed = false;
    private final BlockingQueue<NetworkEvent> inbox = new LinkedBlockingQueue<>();
    private Thread thread;

    public Router(String id, String siteName, List<String> neighborIds, Map<String, Router> directory,
                  AlternatePathCalculator calculator, FailureHistory failureHistory, NetworkObserver observer,
                  long rebootMillis) {
        this(id, siteName, neighborIds, directory, calculator, failureHistory, observer, rebootMillis, 0L);
    }

    public Router(String id, String siteName, List<String> neighborIds, Map<String, Router> directory,
                  AlternatePathCalculator calculator, FailureHistory failureHistory, NetworkObserver observer,
                  long rebootMillis, long cascadeDelayMillis) {
        // Preserves the exact old behavior (recovery dwell = half the reboot dwell) for any
        // caller still using this overload, e.g. FaultInjectionDemoTest's test doubles.
        this(id, siteName, neighborIds, directory, calculator, failureHistory, observer,
                rebootMillis, cascadeDelayMillis, Math.max(1, rebootMillis / 2));
    }

    public Router(String id, String siteName, List<String> neighborIds, Map<String, Router> directory,
                  AlternatePathCalculator calculator, FailureHistory failureHistory, NetworkObserver observer,
                  long rebootMillis, long cascadeDelayMillis, long rebootRecoveryMillis) {
        this.id = id;
        this.siteName = siteName;
        this.neighborIds = neighborIds;
        this.directory = directory;
        this.calculator = calculator;
        this.failureHistory = failureHistory;
        this.observer = observer;
        this.rebootMillis = rebootMillis;
        this.cascadeDelayMillis = cascadeDelayMillis;
        this.rebootRecoveryMillis = rebootRecoveryMillis;
    }

    public RouterState state() {
        return state;
    }

    /**
     * True if this router has EVER encountered the alternate-path bug and crashed during
     * this run, regardless of its current state. {@link #state()} alone understates this:
     * a router that crashed and fully recovered before someone checks reads back as
     * {@code ONLINE}, silently hiding that the bug fired. This flag is the honest record
     * of "did this router encounter the bug," matching what the dashboard log actually
     * showed as the run went on.
     */
    public boolean everCrashed() {
        return everCrashed;
    }

    public List<String> neighborIds() {
        return Collections.unmodifiableList(neighborIds);
    }

    /** Starts this router's dedicated thread. */
    public void start() {
        thread = new Thread(this::runLoop, "router-" + id);
        thread.setDaemon(true);
        thread.start();
    }

    /** Delivers an event into this router's inbox. Safe to call from any thread. */
    public void deliver(NetworkEvent event) {
        inbox.offer(event);
    }

    public void shutdown() {
        deliver(new NetworkEvent(NetworkEvent.Type.SHUTDOWN, null));
    }

    public void join(long millis) throws InterruptedException {
        if (thread != null) {
            thread.join(millis);
        }
    }

    private void runLoop() {
        try {
            while (true) {
                NetworkEvent event = inbox.take();
                if (event.type == NetworkEvent.Type.SHUTDOWN) {
                    return;
                }
                handleEvent(event);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * The state machine. Package-visible logic, deliberately independent of the
     * thread/queue machinery above so it can be unit tested by calling it directly.
     */
    public void handleEvent(NetworkEvent event) {
        switch (event.type) {
            case LINK_FAILED:
                downNeighbors.add(event.neighborId);
                failureHistory.record(Instant.now());
                observer.onLog(id + " (" + siteName + "): link to " + event.neighborId + " failed");
                attemptRecalculation("link to " + event.neighborId + " failed");
                break;

            case NEIGHBOR_CRASHED:
                // The mechanism the incident report describes: "This crash appeared to
                // the network as a site failure" — a neighbor crashing is handled
                // identically to a link failure.
                downNeighbors.add(event.neighborId);
                failureHistory.record(Instant.now());
                observer.onLog(id + " (" + siteName + "): neighbor " + event.neighborId
                        + " crashed — seen as a site failure");
                attemptRecalculation("neighbor " + event.neighborId + " crashed");
                break;

            case LINK_RESTORED:
            case NEIGHBOR_RECOVERED:
                downNeighbors.remove(event.neighborId);
                observer.onLog(id + " (" + siteName + "): link to " + event.neighborId + " restored");
                break;

            case SHUTDOWN:
                break;
        }
    }

    private void attemptRecalculation(String reason) {
        if (state == RouterState.CRASHED || state == RouterState.REBOOTING) {
            // Already down; the environmental event just widens the blast radius for
            // when this router eventually comes back and has to recalculate anyway.
            return;
        }

        transition(RouterState.RECALCULATING, reason);

        List<String> down = new ArrayList<>(downNeighbors);
        int recentFailures = failureHistory.countInWindow(Instant.now());

        try {
            List<String> alternates = calculator.calculateAlternatePath(id, down, recentFailures);
            transition(RouterState.ONLINE, "alternate path(s) found: " + alternates);
        } catch (PathCalculationFault fault) {
            crash(fault);
        }
    }

    private void crash(PathCalculationFault fault) {
        everCrashed = true;
        transition(RouterState.CRASHED, fault.getMessage());

        // The crash is itself a new network-wide failure — this is the feedback loop:
        // "As more routers crashed more calculation events were triggered creating a
        // feedback loop that rapidly worsened."
        failureHistory.record(Instant.now());

        // Deliberate pacing, not part of the mechanism: without this, a crash notifies
        // its neighbors (and they crash in turn) at raw thread-scheduling speed --
        // microseconds -- so a live audience sees every router die in the same instant
        // instead of watching the cascade actually ripple outward wave by wave. This
        // delay is what makes "as more routers crashed more calculation events were
        // triggered" visible rather than merely true.
        if (!sleep(cascadeDelayMillis)) {
            return;
        }

        for (String neighborId : neighborIds) {
            Router neighbor = directory.get(neighborId);
            if (neighbor != null) {
                neighbor.deliver(new NetworkEvent(NetworkEvent.Type.NEIGHBOR_CRASHED, id));
            }
        }

        if (!sleep(rebootMillis)) {
            return;
        }
        transition(RouterState.REBOOTING, "reboot in progress");

        if (!sleep(rebootRecoveryMillis)) {
            return;
        }

        downNeighbors.clear();
        transition(RouterState.ONLINE, "recovered after reboot");

        for (String neighborId : neighborIds) {
            Router neighbor = directory.get(neighborId);
            if (neighbor != null) {
                neighbor.deliver(new NetworkEvent(NetworkEvent.Type.NEIGHBOR_RECOVERED, id));
            }
        }
    }

    private boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void transition(RouterState newState, String reason) {
        RouterState old = state;
        state = newState;
        observer.onRouterStateChanged(id, siteName, old, newState, reason);
    }
}
