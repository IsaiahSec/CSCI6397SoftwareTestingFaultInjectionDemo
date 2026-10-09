package edu.uca.csci6397.dtrsim;

import java.util.ArrayList;
import java.util.List;

/**
 * Starts the web dashboard and falls back to the terminal dashboard if it can't bind its
 * port — per the plan: "Implement [a web dashboard] and [a terminal dashboard] as an
 * option. I can fail back to [the terminal dashboard] if [the web dashboard] fails."
 */
public final class DashboardFactory {

    private DashboardFactory() {
    }

    public static final class Dashboards {
        public final NetworkObserver observer;
        public final WebDashboardObserver web; // null if the web dashboard didn't start

        private Dashboards(NetworkObserver observer, WebDashboardObserver web) {
            this.observer = observer;
            this.web = web;
        }
    }

    public static Dashboards start(String title, int port, NetworkTopology topology) {
        TerminalDashboardObserver terminal = new TerminalDashboardObserver(title);
        List<NetworkObserver> active = new ArrayList<>();
        WebDashboardObserver web = null;

        try {
            WebDashboardObserver candidate = new WebDashboardObserver(title, port);
            candidate.registerTopology(topology);
            candidate.start();
            web = candidate;
            active.add(web);
            System.out.println("Web dashboard live at " + web.url() + " -- open it in a browser now.");
        } catch (Exception e) {
            System.out.println("Web dashboard could not start (" + e.getMessage() + ") — falling back to the terminal dashboard.");
            active.add(terminal);
        }

        NetworkObserver observer = active.size() == 1 ? active.get(0) : new BroadcastObserver(active);
        return new Dashboards(observer, web);
    }
}
