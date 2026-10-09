package edu.uca.csci6397.dtrsim;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Network-wide, shared record of recent link and site failures. This is the thing the
 * incident report means by "the chance of a crash was small, but increased with the
 * number and frequency of link and site failures on the network" — it is deliberately
 * global (one instance shared by every router), not per-router, because the real bug's
 * trigger condition was a property of the whole network's failure rate, not of any one
 * router's own history.
 *
 * <p>A router crash is itself recorded here too (see {@link Router}), which is what
 * turns one crash into a feedback loop: each crash raises the count, which raises the
 * crash probability for the next recalculation, which is more likely to produce another
 * crash.
 */
public final class FailureHistory {

    private final Deque<Instant> events = new ArrayDeque<>();
    private final Duration window;

    public FailureHistory(Duration window) {
        this.window = window;
    }

    public synchronized void record(Instant at) {
        events.addLast(at);
        prune(at);
    }

    public synchronized int countInWindow(Instant now) {
        prune(now);
        return events.size();
    }

    private void prune(Instant now) {
        while (!events.isEmpty() && Duration.between(events.peekFirst(), now).compareTo(window) > 0) {
            events.pollFirst();
        }
    }
}
