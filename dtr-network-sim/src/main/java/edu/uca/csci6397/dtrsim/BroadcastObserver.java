package edu.uca.csci6397.dtrsim;

import java.util.List;

/** Fans every callback out to several observers — used to drive the terminal and web
 *  dashboards at the same time, or just one if the other failed to start. */
public final class BroadcastObserver implements NetworkObserver {

    private final List<NetworkObserver> targets;

    public BroadcastObserver(List<NetworkObserver> targets) {
        this.targets = targets;
    }

    @Override
    public void onRouterStateChanged(String routerId, String siteName, RouterState oldState,
                                      RouterState newState, String reason) {
        for (NetworkObserver o : targets) {
            o.onRouterStateChanged(routerId, siteName, oldState, newState, reason);
        }
    }

    @Override
    public void onLog(String message) {
        for (NetworkObserver o : targets) {
            o.onLog(message);
        }
    }
}
