package edu.uca.csci6397.dtrsim;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Redraws a live ANSI dashboard in the terminal: one line per router (site, state,
 * last reason), plus a scrolling tail of recent events underneath. Works in any
 * ANSI-capable terminal (every normal Mac/Linux terminal, Windows Terminal, WSL); no
 * dependencies beyond the JDK.
 */
public final class TerminalDashboardObserver implements NetworkObserver {

    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String DIM = "\u001B[2m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String MAGENTA = "\u001B[35m";
    private static final String CYAN = "\u001B[36m";
    private static final String CLEAR = "\u001B[2J\u001B[H";

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int LOG_LINES = 14;

    private final String title;
    private final Map<String, RouterRow> rows = new LinkedHashMap<>();
    private final ArrayDeque<String> log = new ArrayDeque<>();

    public TerminalDashboardObserver(String title) {
        this.title = title;
    }

    private static final class RouterRow {
        String siteName;
        RouterState state = RouterState.ONLINE;
        String reason = "normal operation";
    }

    @Override
    public synchronized void onRouterStateChanged(String routerId, String siteName, RouterState oldState,
                                                    RouterState newState, String reason) {
        RouterRow row = rows.computeIfAbsent(routerId, k -> new RouterRow());
        row.siteName = siteName;
        row.state = newState;
        row.reason = reason;
        appendLog(String.format("[%s] %-4s %-18s %s -> %s  (%s)",
                now(), routerId, siteName, oldState, newState, truncate(reason, 70)));
        redraw();
    }

    @Override
    public synchronized void onLog(String message) {
        appendLog("[" + now() + "] " + message);
        redraw();
    }

    private void appendLog(String line) {
        log.addLast(line);
        while (log.size() > LOG_LINES) {
            log.removeFirst();
        }
    }

    private void redraw() {
        StringBuilder sb = new StringBuilder();
        sb.append(CLEAR);
        sb.append(BOLD).append(title).append(RESET).append('\n');
        sb.append(DIM).append("=".repeat(Math.min(100, title.length() + 20))).append(RESET).append('\n').append('\n');

        for (RouterRow row : rows.values()) {
            // not reached before first registration; rows populated lazily below
        }
        for (String id : rows.keySet()) {
            RouterRow row = rows.get(id);
            sb.append(colorFor(row.state))
                    .append(String.format("%-4s %-18s %-13s", id, row.siteName, row.state))
                    .append(RESET)
                    .append("  ").append(DIM).append(truncate(row.reason, 60)).append(RESET)
                    .append('\n');
        }

        sb.append('\n').append(DIM).append("-".repeat(100)).append(RESET).append('\n');
        for (String line : log) {
            sb.append(line).append('\n');
        }

        System.out.print(sb);
        System.out.flush();
    }

    private String colorFor(RouterState state) {
        switch (state) {
            case ONLINE: return GREEN;
            case RECALCULATING: return YELLOW;
            case CRASHED: return RED;
            case REBOOTING: return MAGENTA;
            default: return CYAN;
        }
    }

    private String now() {
        return LocalTime.now().format(TIME_FMT);
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
