package edu.uca.csci6397.dtrsim;

/**
 * Callback the simulation drives to update whatever is visualizing it. {@link Router}
 * never knows or cares whether that's a terminal dashboard, a web dashboard, both, or
 * neither — it just reports what happened.
 */
public interface NetworkObserver {

    void onRouterStateChanged(String routerId, String siteName, RouterState oldState, RouterState newState, String reason);

    void onLog(String message);
}
