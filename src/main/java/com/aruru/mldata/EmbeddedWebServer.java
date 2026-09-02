package com.aruru.mldata;

import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EmbeddedWebServer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private HttpServer server;
    private final int port;
    private final MySQLManager mySQLManager;

    public EmbeddedWebServer(int port, MySQLManager mySQLManager) {
        this.port = port;
        this.mySQLManager = mySQLManager;
    }

    public EmbeddedWebServer(int port) {
        this(port, null);
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", new RootHandler());
            server.createContext("/status", new StatusHandler());
            server.createContext("/api/status", new StatusHandler());
            server.createContext("/api/stats", new StatsHandler());
            server.createContext("/api/movements", new MovementsHandler());
            server.createContext("/api/blocks", new BlocksHandler());
            server.createContext("/api/chat", new ChatHandler());
            server.setExecutor(null);
            server.start();
            LOGGER.info("[MLDataCollector] Embedded web server started on port {}", port);
        } catch (IOException e) {
            LOGGER.error("[MLDataCollector] Failed to start embedded web server on port {}: {}", port, e.getMessage(), e);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            LOGGER.info("[MLDataCollector] Embedded web server stopped.");
        }
    }

    // ---- shared helpers ----

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> map = new HashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) return map;
        for (String pair : rawQuery.split("&")) {
            int idx = pair.indexOf('=');
            try {
                if (idx > 0) {
                    String k = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                    String v = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                    map.put(k, v);
                } else {
                    map.put(URLDecoder.decode(pair, StandardCharsets.UTF_8), "");
                }
            } catch (Exception ignored) {}
        }
        return map;
    }

    private static int parseIntParam(Map<String, String> q, String key, int def, int min, int max) {
        try {
            int v = Integer.parseInt(q.getOrDefault(key, String.valueOf(def)));
            if (v < min) return min;
            if (v > max) return max;
            return v;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static long parseLongParam(Map<String, String> q, String key, long def) {
        try {
            return Long.parseLong(q.getOrDefault(key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static void setCors(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, X-API-Key");
    }

    private static void sendJson(HttpExchange ex, int code, String json) throws IOException {
        setCors(ex);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private boolean handleOptions(HttpExchange ex) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            setCors(ex);
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return true;
        }
        return false;
    }

    // ---- handlers ----

    static class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCors(exchange);
            String response = "{\"status\": \"running\", \"mod\": \"MLDataCollector\", \"version\": \"1.0-SNAPSHOT\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    static class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCors(exchange);
            String path = exchange.getRequestURI().getPath();
            if (!"/".equals(path)) {
                String notFound = "{\"error\": \"not_found\", \"path\": \"" + path + "\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                byte[] nfBytes = notFound.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(404, nfBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(nfBytes);
                }
                return;
            }
            String html = "<!doctype html><html><head><meta charset=\"utf-8\"><title>MLDataCollector</title></head>"
                    + "<body style=\"font-family:sans-serif;padding:24px\">"
                    + "<h1>MLDataCollector</h1>"
                    + "<p>Status: running (NeoForge 1.21.1, MySQL)</p>"
                    + "<ul>"
                    + "<li><a href=\"/status\">/status</a> - JSON status</li>"
                    + "<li><a href=\"/api/status\">/api/status</a> - alias</li>"
                    + "<li><a href=\"/api/stats\">/api/stats</a> - counts</li>"
                    + "<li><a href=\"/api/movements?limit=20\">/api/movements?limit=20&amp;since=0</a></li>"
                    + "<li><a href=\"/api/blocks?limit=20\">/api/blocks?limit=20&amp;since=0</a></li>"
                    + "<li><a href=\"/api/chat?limit=20\">/api/chat?limit=20&amp;since=0</a></li>"
                    + "</ul>"
                    + "<p>Port: 8974</p></body></html>";
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    class StatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (handleOptions(ex)) return;
            if (mySQLManager == null) {
                sendJson(ex, 503, "{\"error\":\"db_not_initialized\"}");
                return;
            }
            try (Connection conn = mySQLManager.getConnection()) {
                long movements = count(conn, "ml_movements");
                long blocks = count(conn, "ml_blocks");
                long chats = count(conn, "ml_chat");
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("movements", movements);
                out.put("blocks", blocks);
                out.put("chats", chats);
                sendJson(ex, 200, GSON.toJson(out));
            } catch (SQLException e) {
                LOGGER.error("[MLDataCollector] /api/stats failed: {}", e.getMessage(), e);
                sendJson(ex, 500, "{\"error\":\"db_error\",\"message\":\"" + escape(e.getMessage()) + "\"}");
            }
        }

        private long count(Connection conn, String table) throws SQLException {
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM " + table);
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    class MovementsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (handleOptions(ex)) return;
            if (mySQLManager == null) {
                sendJson(ex, 503, "{\"error\":\"db_not_initialized\"}");
                return;
            }
            Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());
            int limit = parseIntParam(q, "limit", 20, 1, 100);
            long since = parseLongParam(q, "since", 0);
            String player = q.get("player");

            boolean filterSince = since > 0;
            boolean filterPlayer = player != null && !player.isEmpty();
            StringBuilder sql = new StringBuilder("SELECT id, player_name, player_uuid, world, x, y, z, yaw, pitch, timestamp FROM ml_movements");
            List<String> where = new ArrayList<>();
            if (filterSince) where.add("id > ?");
            if (filterPlayer) where.add("player_name = ?");
            if (!where.isEmpty()) sql.append(" WHERE ").append(String.join(" AND ", where));
            sql.append(" ORDER BY id DESC LIMIT ?");

            try (Connection conn = mySQLManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                int idx = 1;
                if (filterSince) ps.setLong(idx++, since);
                if (filterPlayer) ps.setString(idx++, player);
                ps.setInt(idx, limit);
                List<Map<String, Object>> rows = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> r = new LinkedHashMap<>();
                        r.put("id", rs.getLong("id"));
                        r.put("player_name", rs.getString("player_name"));
                        r.put("player_uuid", rs.getString("player_uuid"));
                        r.put("world", rs.getString("world"));
                        r.put("x", rs.getDouble("x"));
                        r.put("y", rs.getDouble("y"));
                        r.put("z", rs.getDouble("z"));
                        r.put("yaw", rs.getFloat("yaw"));
                        r.put("pitch", rs.getFloat("pitch"));
                        r.put("timestamp", rs.getLong("timestamp"));
                        rows.add(r);
                    }
                }
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("count", rows.size());
                out.put("limit", limit);
                out.put("since", since);
                out.put("data", rows);
                sendJson(ex, 200, GSON.toJson(out));
            } catch (SQLException e) {
                LOGGER.error("[MLDataCollector] /api/movements failed: {}", e.getMessage(), e);
                sendJson(ex, 500, "{\"error\":\"db_error\",\"message\":\"" + escape(e.getMessage()) + "\"}");
            }
        }
    }

    class BlocksHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (handleOptions(ex)) return;
            if (mySQLManager == null) {
                sendJson(ex, 503, "{\"error\":\"db_not_initialized\"}");
                return;
            }
            Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());
            int limit = parseIntParam(q, "limit", 20, 1, 100);
            long since = parseLongParam(q, "since", 0);
            String action = q.get("action");

            boolean filterSince = since > 0;
            boolean filterAction = action != null && !action.isEmpty();
            StringBuilder sql = new StringBuilder("SELECT id, action, player_name, block_type, world, x, y, z, timestamp FROM ml_blocks");
            List<String> where = new ArrayList<>();
            if (filterSince) where.add("id > ?");
            if (filterAction) where.add("action = ?");
            if (!where.isEmpty()) sql.append(" WHERE ").append(String.join(" AND ", where));
            sql.append(" ORDER BY id DESC LIMIT ?");

            try (Connection conn = mySQLManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                int idx = 1;
                if (filterSince) ps.setLong(idx++, since);
                if (filterAction) ps.setString(idx++, action);
                ps.setInt(idx, limit);
                List<Map<String, Object>> rows = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> r = new LinkedHashMap<>();
                        r.put("id", rs.getLong("id"));
                        r.put("action", rs.getString("action"));
                        r.put("player_name", rs.getString("player_name"));
                        r.put("block_type", rs.getString("block_type"));
                        r.put("world", rs.getString("world"));
                        r.put("x", rs.getInt("x"));
                        r.put("y", rs.getInt("y"));
                        r.put("z", rs.getInt("z"));
                        r.put("timestamp", rs.getLong("timestamp"));
                        rows.add(r);
                    }
                }
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("count", rows.size());
                out.put("limit", limit);
                out.put("since", since);
                out.put("data", rows);
                sendJson(ex, 200, GSON.toJson(out));
            } catch (SQLException e) {
                LOGGER.error("[MLDataCollector] /api/blocks failed: {}", e.getMessage(), e);
                sendJson(ex, 500, "{\"error\":\"db_error\",\"message\":\"" + escape(e.getMessage()) + "\"}");
            }
        }
    }

    class ChatHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (handleOptions(ex)) return;
            if (mySQLManager == null) {
                sendJson(ex, 503, "{\"error\":\"db_not_initialized\"}");
                return;
            }
            Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());
            int limit = parseIntParam(q, "limit", 20, 1, 100);
            long since = parseLongParam(q, "since", 0);

            String sql = since > 0
                    ? "SELECT id, player_name, is_command, message, world, timestamp FROM ml_chat WHERE id > ? ORDER BY id DESC LIMIT ?"
                    : "SELECT id, player_name, is_command, message, world, timestamp FROM ml_chat ORDER BY id DESC LIMIT ?";

            try (Connection conn = mySQLManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                if (since > 0) {
                    ps.setLong(1, since);
                    ps.setInt(2, limit);
                } else {
                    ps.setInt(1, limit);
                }
                List<Map<String, Object>> rows = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> r = new LinkedHashMap<>();
                        r.put("id", rs.getLong("id"));
                        r.put("player_name", rs.getString("player_name"));
                        r.put("is_command", rs.getInt("is_command"));
                        r.put("message", rs.getString("message"));
                        r.put("world", rs.getString("world"));
                        r.put("timestamp", rs.getLong("timestamp"));
                        rows.add(r);
                    }
                }
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("count", rows.size());
                out.put("limit", limit);
                out.put("since", since);
                out.put("data", rows);
                sendJson(ex, 200, GSON.toJson(out));
            } catch (SQLException e) {
                LOGGER.error("[MLDataCollector] /api/chat failed: {}", e.getMessage(), e);
                sendJson(ex, 500, "{\"error\":\"db_error\",\"message\":\"" + escape(e.getMessage()) + "\"}");
            }
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
