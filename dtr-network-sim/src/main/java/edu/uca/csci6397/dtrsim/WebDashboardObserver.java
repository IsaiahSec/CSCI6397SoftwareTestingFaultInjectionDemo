package edu.uca.csci6397.dtrsim;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Live network dashboard served over plain HTTP using only {@code com.sun.net.httpserver}
 * (ships in every JDK — nothing to download from Maven Central, which this sandbox can't
 * reach anyway). The browser page polls {@code /api/state} every 400ms and redraws a grid
 * of router cards plus an event log; no WebSocket, no external JS library.
 *
 * <p>Per the user's own fallback plan ("I can fail back to [the terminal dashboard] if
 * [the web dashboard] fails"), construction never throws past this class's own {@code start()}
 * — a caller should wrap {@code start()} in a try/catch and fall back to
 * {@link TerminalDashboardObserver} on failure (e.g. the port is already taken).
 */
public final class WebDashboardObserver implements NetworkObserver {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int LOG_LINES = 60;

    private final String title;
    private final int port;
    private HttpServer server;
    private ExecutorService executor;

    private final Map<String, RouterRow> rows = new LinkedHashMap<>();
    private final ArrayDeque<String> log = new ArrayDeque<>();

    public WebDashboardObserver(String title, int port) {
        this.title = title;
        this.port = port;
    }

    private static final class RouterRow {
        String siteName;
        RouterState state = RouterState.ONLINE;
        String reason = "normal operation";
        List<String> neighbors = List.of();
    }

    /** Registers every router up front so the grid has a stable, complete layout from frame one. */
    public synchronized void registerTopology(NetworkTopology topology) {
        for (Router router : topology.routers) {
            RouterRow row = rows.computeIfAbsent(router.id, k -> new RouterRow());
            row.siteName = router.siteName;
            row.neighbors = router.neighborIds();
        }
    }

    /** Starts the HTTP server. Throws if the port can't be bound — callers should catch and fall back. */
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        ThreadFactory daemonFactory = runnable -> {
            Thread t = new Thread(runnable, "web-dashboard-http");
            t.setDaemon(true);
            return t;
        };
        executor = Executors.newFixedThreadPool(4, daemonFactory);
        server.setExecutor(executor);
        server.createContext("/", this::serveIndex);
        server.createContext("/api/state", this::serveState);
        server.start();
    }

    /** Stops the server AND its executor — without this, the executor's threads (even
     *  though they're now daemon threads) keep the connection pool alive longer than
     *  needed between runs, and on some JDKs HttpServer.stop(0) alone doesn't release
     *  the listening socket promptly enough for the next run to rebind the same port. */
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
            try {
                executor.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public String url() {
        return "http://127.0.0.1:" + port + "/";
    }

    @Override
    public synchronized void onRouterStateChanged(String routerId, String siteName, RouterState oldState,
                                                    RouterState newState, String reason) {
        RouterRow row = rows.computeIfAbsent(routerId, k -> new RouterRow());
        row.siteName = siteName;
        row.state = newState;
        row.reason = reason;
        appendLog(String.format("[%s] %s (%s): %s -> %s (%s)", now(), routerId, siteName, oldState, newState, reason));
    }

    @Override
    public synchronized void onLog(String message) {
        appendLog("[" + now() + "] " + message);
    }

    private void appendLog(String line) {
        log.addLast(line);
        while (log.size() > LOG_LINES) {
            log.removeFirst();
        }
    }

    private String now() {
        return LocalTime.now().format(TIME_FMT);
    }

    // ---- HTTP handlers -------------------------------------------------

    private void serveIndex(HttpExchange exchange) throws IOException {
        byte[] body = INDEX_HTML.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private synchronized void serveState(HttpExchange exchange) throws IOException {
        StringBuilder json = new StringBuilder();
        json.append("{\"title\":").append(jsonString(title)).append(",\"routers\":[");
        boolean first = true;
        for (String id : rows.keySet()) {
            RouterRow row = rows.get(id);
            if (!first) json.append(',');
            first = false;
            json.append("{\"id\":").append(jsonString(id))
                    .append(",\"site\":").append(jsonString(row.siteName))
                    .append(",\"state\":").append(jsonString(row.state.name()))
                    .append(",\"reason\":").append(jsonString(row.reason))
                    .append(",\"neighbors\":[");
            boolean firstN = true;
            for (String n : row.neighbors) {
                if (!firstN) json.append(',');
                firstN = false;
                json.append(jsonString(n));
            }
            json.append("]}");
        }
        json.append("],\"log\":[");
        boolean firstL = true;
        for (String line : log) {
            if (!firstL) json.append(',');
            firstL = false;
            json.append(jsonString(line));
        }
        json.append("]}");

        byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().add("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private static String jsonString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.append('"').toString();
    }

    // ---- Static page -----------------------------------------------------

    private static final String INDEX_HTML = """
            <!doctype html>
            <html>
            <head>
            <meta charset="utf-8">
            <title>DTR Network Dashboard</title>
            <style>
              :root { color-scheme: dark; }
              * { box-sizing: border-box; }
              body {
                margin: 0; padding: 24px;
                background: #11151a; color: #d8dee4;
                font-family: -apple-system, Segoe UI, Roboto, Helvetica, Arial, sans-serif;
              }
              h1 { font-size: 20px; margin: 0 0 4px; color: #f2f5f8; }
              .subtitle { color: #7d8891; font-size: 13px; margin-bottom: 20px; }
              .grid {
                display: grid;
                grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
                gap: 12px;
                margin-bottom: 24px;
              }
              .card {
                border-radius: 10px;
                padding: 12px 14px;
                background: #171c22;
                border: 1px solid #262c33;
                transition: border-color 120ms, box-shadow 120ms;
              }
              .card .id { font-size: 12px; color: #7d8891; letter-spacing: 0.04em; }
              .card .site { font-size: 15px; font-weight: 600; color: #f2f5f8; margin: 2px 0 8px; }
              .card .state {
                display: inline-block; font-size: 11px; font-weight: 700;
                letter-spacing: 0.06em; text-transform: uppercase;
                padding: 3px 8px; border-radius: 999px; margin-bottom: 8px;
              }
              .card .reason { font-size: 12px; color: #9aa4ad; line-height: 1.35; }
              .state-ONLINE { background: #113524; color: #52d17c; }
              .state-RECALCULATING { background: #3a2f10; color: #e8b84b; }
              .state-CRASHED { background: #3a1414; color: #f0695f; }
              .state-REBOOTING { background: #2a1640; color: #c58df0; }
              .card.ONLINE { border-color: #1f4430; }
              .card.RECALCULATING { border-color: #4a3b14; }
              .card.CRASHED { border-color: #4a1c1c; box-shadow: 0 0 0 1px rgba(240,105,95,0.25); }
              .card.REBOOTING { border-color: #3a1f5c; }
              .log {
                background: #0c0f12; border: 1px solid #262c33; border-radius: 10px;
                padding: 12px 14px; height: 260px; overflow-y: auto;
                font-family: "SF Mono", Menlo, Consolas, monospace; font-size: 12px;
                color: #9aa4ad; line-height: 1.5;
              }
              .log div:last-child { color: #d8dee4; }
            </style>
            </head>
            <body>
              <h1 id="title">DTR Network Dashboard</h1>
              <div class="subtitle">Updates automatically &middot; each card is one router's own independent thread</div>
              <div class="grid" id="grid"></div>
              <div class="log" id="log"></div>
              <script>
                async function tick() {
                  try {
                    const res = await fetch('/api/state', { cache: 'no-store' });
                    const data = await res.json();
                    document.getElementById('title').textContent = data.title;
                    const grid = document.getElementById('grid');
                    grid.innerHTML = '';
                    for (const r of data.routers) {
                      const card = document.createElement('div');
                      card.className = 'card ' + r.state;
                      card.innerHTML =
                        '<div class="id">' + r.id + '</div>' +
                        '<div class="site">' + r.site + '</div>' +
                        '<div class="state state-' + r.state + '">' + r.state + '</div>' +
                        '<div class="reason">' + escapeHtml(r.reason) + '</div>';
                      grid.appendChild(card);
                    }
                    const log = document.getElementById('log');
                    const atBottom = log.scrollTop + log.clientHeight >= log.scrollHeight - 4;
                    log.innerHTML = data.log.map(l => '<div>' + escapeHtml(l) + '</div>').join('');
                    if (atBottom) log.scrollTop = log.scrollHeight;
                  } catch (e) {
                    // server not up yet / between requests; ignore and retry
                  }
                }
                function escapeHtml(s) {
                  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
                }
                tick();
                setInterval(tick, 400);
              </script>
            </body>
            </html>
            """;
}
