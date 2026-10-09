package edu.uca.csci6397.dtrsim;

/**
 * Lets {@link NetworkTopology#build} wire routers to an observer before that observer
 * actually exists. The dashboard needs the topology (to list routers up front); the
 * topology's routers need the dashboard as their observer. This breaks the cycle: build
 * the topology against one of these (silently dropping events), hand the topology to the
 * dashboard factory, then point this at the real dashboard once it's running.
 */
public final class DelegatingObserver implements NetworkObserver {

    private volatile NetworkObserver delegate = NO_OP;

    public void setDelegate(NetworkObserver delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onRouterStateChanged(String routerId, String siteName, RouterState oldState,
                                      RouterState newState, String reason) {
        delegate.onRouterStateChanged(routerId, siteName, oldState, newState, reason);
    }

    @Override
    public void onLog(String message) {
        delegate.onLog(message);
    }

    private static final NetworkObserver NO_OP = new NetworkObserver() {
        @Override public void onRouterStateChanged(String routerId, String siteName, RouterState oldState, RouterState newState, String reason) { }
        @Override public void onLog(String message) { }
    };
}
