package com.aruru.mldata;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

public class WebServerManager {
    private HttpServer server;
    private final DatabaseManager dbManager;
    private final Gson gson;
    private final JavaPlugin plugin;
    private final String correctPassword;
    private final String sessionToken;

    public WebServerManager(JavaPlugin plugin, DatabaseManager dbManager, int port, String password) {
        this.plugin = plugin;
        this.dbManager = dbManager;
        this.gson = new Gson();
        this.correctPassword = password;
        this.sessionToken = UUID.randomUUID().toString();

        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            
            // SPA and static files (No more BasicAuthenticator!)
            server.createContext("/", this::serveStaticFile);
            
            // API endpoints
            server.createContext("/api/login", this::handleLogin);
            server.createContext("/api/metrics", this::handleMetricsApi);
            server.createContext("/api/stats", this::handleStatsApi);
            server.createContext("/api/events/blocks", exchange -> handleEventsApi(exchange, "block_events"));
            server.createContext("/api/events/deaths", exchange -> handleEventsApi(exchange, "death_events"));
            server.createContext("/api/events/interactions", exchange -> handleEventsApi(exchange, "interaction_events"));
            server.createContext("/api/events/damage", exchange -> handleEventsApi(exchange, "damage_events"));
            server.createContext("/api/events/redstone", exchange -> handleEventsApi(exchange, "redstone_events"));
            server.createContext("/api/events/items", exchange -> handleEventsApi(exchange, "item_events"));
            server.createContext("/api/events/chat", exchange -> handleEventsApi(exchange, "chat_events"));
            server.createContext("/api/events/sessions", exchange -> handleEventsApi(exchange, "session_events"));
            server.createContext("/api/events/movement", exchange -> handleEventsApi(exchange, "player_movement"));
            
            server.setExecutor(Executors.newFixedThreadPool(4));
            server.start();
            plugin.getLogger().info("Web Server started on port " + port);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to start web server: " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null) server.stop(0);
    }
    
    private boolean checkAuth(HttpExchange exchange) {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.equals("Bearer " + sessionToken)) {
            return true;
        }
        sendError(exchange, 401, "Unauthorized");
        return false;
    }
    
    private void sendError(HttpExchange exchange, int code, String msg) {
        try {
            JsonObject res = new JsonObject();
            res.addProperty("error", msg);
            byte[] bytes = gson.toJson(res).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        } catch (Exception ignored) {}
    }

    private void handleLogin(HttpExchange exchange) {
        try {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                JsonObject req = JsonParser.parseReader(isr).getAsJsonObject();
                if (req.has("password") && req.get("password").getAsString().equals(correctPassword)) {
                    JsonObject res = new JsonObject();
                    res.addProperty("token", sessionToken);
                    byte[] bytes = gson.toJson(res).getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, bytes.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(bytes);
                    os.close();
                    return;
                }
            }
            sendError(exchange, 401, "Invalid password");
        } catch (Exception e) {
            sendError(exchange, 500, "Server error");
        }
    }

    private void serveStaticFile(HttpExchange exchange) {
        try {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) path = "/web/index.html";
            else path = "/web" + path;

            try (InputStream is = plugin.getResource(path.substring(1))) { 
                if (is == null) {
                    sendError(exchange, 404, "Not Found");
                    return;
                }
                byte[] content = is.readAllBytes();
                if (path.endsWith(".html")) exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                else if (path.endsWith(".css")) exchange.getResponseHeaders().set("Content-Type", "text/css; charset=UTF-8");
                else if (path.endsWith(".js")) exchange.getResponseHeaders().set("Content-Type", "application/javascript; charset=UTF-8");
                
                exchange.sendResponseHeaders(200, content.length);
                OutputStream os = exchange.getResponseBody();
                os.write(content);
                os.close();
            }
        } catch (Exception e) {
            sendError(exchange, 500, "Server error");
        }
    }

    private void handleMetricsApi(HttpExchange exchange) {
        if (!checkAuth(exchange)) return;
        try {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            String json = "";
            if (dbManager.getType().equals("sqlite")) {
                List<Map<String, Object>> metrics = new ArrayList<>();
                try (Connection conn = dbManager.getSQLiteConnection(); Statement stmt = conn.createStatement()) {
                    ResultSet rs = stmt.executeQuery("SELECT * FROM server_metrics ORDER BY id DESC LIMIT 60");
                    while (rs.next()) {
                        Map<String, Object> data = new HashMap<>();
                        data.put("tps", rs.getDouble("tps"));
                        data.put("ram_used_mb", rs.getLong("ram_used_mb"));
                        data.put("online_players", rs.getInt("online_players"));
                        data.put("timestamp", rs.getLong("timestamp"));
                        metrics.add(0, data); 
                    }
                }
                json = gson.toJson(metrics);
            } else {
                json = fetchFromSupabase("server_metrics?select=tps,ram_used_mb,online_players,timestamp&order=id.desc&limit=60");
                JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
                JsonArray reversed = new JsonArray();
                for (int i = arr.size() - 1; i >= 0; i--) reversed.add(arr.get(i));
                json = gson.toJson(reversed);
            }
            
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        } catch (Exception e) { sendError(exchange, 500, e.getMessage()); }
    }
    
    private void handleStatsApi(HttpExchange exchange) {
        if (!checkAuth(exchange)) return;
        try {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            JsonObject stats = new JsonObject();
            if (dbManager.getType().equals("sqlite")) {
                try (Connection conn = dbManager.getSQLiteConnection(); Statement stmt = conn.createStatement()) {
                    ResultSet rsBlocks = stmt.executeQuery("SELECT COUNT(*) as c FROM block_events");
                    if (rsBlocks.next()) stats.addProperty("total_blocks", rsBlocks.getInt("c"));
                    ResultSet rsMove = stmt.executeQuery("SELECT COUNT(*) as c FROM player_movement");
                    if (rsMove.next()) stats.addProperty("total_movements", rsMove.getInt("c"));
                    ResultSet rsDeath = stmt.executeQuery("SELECT COUNT(*) as c FROM death_events");
                    if (rsDeath.next()) stats.addProperty("total_deaths", rsDeath.getInt("c"));
                    ResultSet rsInt = stmt.executeQuery("SELECT COUNT(*) as c FROM interaction_events");
                    if (rsInt.next()) stats.addProperty("total_interactions", rsInt.getInt("c"));
                    ResultSet rsDam = stmt.executeQuery("SELECT COUNT(*) as c FROM damage_events");
                    if (rsDam.next()) stats.addProperty("total_damage_events", rsDam.getInt("c"));
                    ResultSet rsRed = stmt.executeQuery("SELECT COUNT(*) as c FROM redstone_events");
                    if (rsRed.next()) stats.addProperty("total_redstone", rsRed.getInt("c"));
                    ResultSet rsItems = stmt.executeQuery("SELECT COUNT(*) as c FROM item_events");
                    if (rsItems.next()) stats.addProperty("total_items", rsItems.getInt("c"));
                    ResultSet rsChat = stmt.executeQuery("SELECT COUNT(*) as c FROM chat_events");
                    if (rsChat.next()) stats.addProperty("total_chat", rsChat.getInt("c"));
                }
            } else {
                stats.addProperty("total_blocks", fetchCountFromSupabase("block_events"));
                stats.addProperty("total_movements", fetchCountFromSupabase("player_movement"));
                stats.addProperty("total_deaths", fetchCountFromSupabase("death_events"));
                stats.addProperty("total_interactions", fetchCountFromSupabase("interaction_events"));
                stats.addProperty("total_damage_events", fetchCountFromSupabase("damage_events"));
                stats.addProperty("total_redstone", fetchCountFromSupabase("redstone_events"));
                stats.addProperty("total_items", fetchCountFromSupabase("item_events"));
                stats.addProperty("total_chat", fetchCountFromSupabase("chat_events"));
            }
            byte[] bytes = gson.toJson(stats).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        } catch (Exception e) { sendError(exchange, 500, e.getMessage()); }
    }

    private void handleEventsApi(HttpExchange exchange, String tableName) {
        if (!checkAuth(exchange)) return;
        try {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            String json = "";
            if (dbManager.getType().equals("sqlite")) {
                List<Map<String, Object>> events = new ArrayList<>();
                try (Connection conn = dbManager.getSQLiteConnection(); Statement stmt = conn.createStatement()) {
                    ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName + " ORDER BY id DESC LIMIT 100");
                    ResultSetMetaData md = rs.getMetaData();
                    int columns = md.getColumnCount();
                    while (rs.next()) {
                        Map<String, Object> row = new HashMap<>();
                        for (int i = 1; i <= columns; ++i) {
                            row.put(md.getColumnName(i), rs.getObject(i));
                        }
                        events.add(row);
                    }
                }
                json = gson.toJson(events);
            } else {
                json = fetchFromSupabase(tableName + "?order=id.desc&limit=100");
            }
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        } catch (Exception e) { sendError(exchange, 500, e.getMessage()); }
    }

    private String fetchFromSupabase(String query) throws Exception {
        URL url = new URL(dbManager.getSupabaseUrl() + "/rest/v1/" + query);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("apikey", dbManager.getSupabaseKey());
        conn.setRequestProperty("Authorization", "Bearer " + dbManager.getSupabaseKey());
        try (InputStream is = conn.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    
    private int fetchCountFromSupabase(String table) {
        try {
            URL url = new URL(dbManager.getSupabaseUrl() + "/rest/v1/" + table + "?select=id&limit=1");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setRequestProperty("apikey", dbManager.getSupabaseKey());
            conn.setRequestProperty("Authorization", "Bearer " + dbManager.getSupabaseKey());
            conn.setRequestProperty("Prefer", "count=exact");
            String countRange = conn.getHeaderField("Content-Range");
            if (countRange != null && countRange.contains("/")) {
                return Integer.parseInt(countRange.split("/")[1]);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to fetch count for " + table + " from Supabase.");
        }
        return 0;
    }
}
