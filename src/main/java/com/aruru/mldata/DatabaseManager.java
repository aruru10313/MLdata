package com.aruru.mldata;

import com.google.gson.Gson;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class DatabaseManager {
    private final String type;
    private final String supabaseUrl;
    private final String supabaseKey;
    private String sqliteUrl;
    private final Logger logger;
    private final Gson gson = new Gson();

    public DatabaseManager(JavaPlugin plugin) {
        this.logger = plugin.getLogger();
        this.type = plugin.getConfig().getString("database.type", "sqlite").toLowerCase();
        this.supabaseUrl = plugin.getConfig().getString("database.supabase-url", "");
        this.supabaseKey = plugin.getConfig().getString("database.supabase-key", "");
        
        if (this.type.equals("sqlite")) {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            File dbFile = new File(plugin.getDataFolder(), "data.db");
            this.sqliteUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            initializeSQLiteTables();
        } else {
            logger.info("Using Supabase (Cloud) for MLDataCollector database.");
        }
    }
    
    public Connection getSQLiteConnection() throws SQLException {
        if (!type.equals("sqlite")) throw new SQLException("Not using SQLite mode");
        return DriverManager.getConnection(sqliteUrl);
    }
    
    public String getType() { return type; }
    public String getSupabaseUrl() { return supabaseUrl; }
    public String getSupabaseKey() { return supabaseKey; }

    private void initializeSQLiteTables() {
        try (Connection conn = getSQLiteConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS server_metrics (id INTEGER PRIMARY KEY AUTOINCREMENT, tps REAL, ram_used_mb INTEGER, ram_max_mb INTEGER, loaded_chunks INTEGER, entity_count INTEGER, online_players INTEGER, timestamp BIGINT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS player_movement (id INTEGER PRIMARY KEY AUTOINCREMENT, player_name TEXT, player_uuid TEXT, world TEXT, x REAL, y REAL, z REAL, yaw REAL, pitch REAL, timestamp BIGINT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS block_events (id INTEGER PRIMARY KEY AUTOINCREMENT, action TEXT, player_name TEXT, block_type TEXT, world TEXT, x INTEGER, y INTEGER, z INTEGER, timestamp BIGINT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS death_events (id INTEGER PRIMARY KEY AUTOINCREMENT, player_name TEXT, death_message TEXT, cause TEXT, world TEXT, x REAL, y REAL, z REAL, timestamp BIGINT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS redstone_events (id INTEGER PRIMARY KEY AUTOINCREMENT, block_type TEXT, world TEXT, x INTEGER, y INTEGER, z INTEGER, old_current INTEGER, new_current INTEGER, timestamp BIGINT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS interaction_events (id INTEGER PRIMARY KEY AUTOINCREMENT, player_name TEXT, target_type TEXT, target_name TEXT, action TEXT, world TEXT, x REAL, y REAL, z REAL, timestamp BIGINT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS damage_events (id INTEGER PRIMARY KEY AUTOINCREMENT, attacker_name TEXT, victim_name TEXT, damage REAL, cause TEXT, world TEXT, x REAL, y REAL, z REAL, timestamp BIGINT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS item_events (id INTEGER PRIMARY KEY AUTOINCREMENT, action TEXT, player_name TEXT, item_type TEXT, amount INTEGER, world TEXT, x REAL, y REAL, z REAL, timestamp BIGINT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS chat_events (id INTEGER PRIMARY KEY AUTOINCREMENT, player_name TEXT, is_command INTEGER, message TEXT, world TEXT, x REAL, y REAL, z REAL, timestamp BIGINT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS session_events (id INTEGER PRIMARY KEY AUTOINCREMENT, player_name TEXT, action TEXT, ip_address TEXT, timestamp BIGINT)");
            logger.info("SQLite Database initialized successfully.");
        } catch (SQLException e) {
            logger.severe("Could not initialize database tables: " + e.getMessage());
        }
    }

    public void insertMetricsAsync(List<Map<String, Object>> metrics) {
        if (metrics.isEmpty()) return;
        CompletableFuture.runAsync(() -> insertMetricsSync(metrics));
    }

    public void insertMetricsSync(List<Map<String, Object>> metrics) {
        if (metrics.isEmpty()) return;
        if (type.equals("sqlite")) {
            String sql = "INSERT INTO server_metrics (tps, ram_used_mb, ram_max_mb, loaded_chunks, entity_count, online_players, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = getSQLiteConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                conn.setAutoCommit(false);
                for (Map<String, Object> data : metrics) {
                    pstmt.setDouble(1, (Double) data.get("tps"));
                    pstmt.setLong(2, (Long) data.get("ram_used_mb"));
                    pstmt.setLong(3, (Long) data.get("ram_max_mb"));
                    pstmt.setInt(4, (Integer) data.get("loaded_chunks"));
                    pstmt.setInt(5, (Integer) data.get("entity_count"));
                    pstmt.setInt(6, (Integer) data.get("online_players"));
                    pstmt.setLong(7, (Long) data.get("timestamp"));
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
                conn.commit();
            } catch (SQLException e) { logger.severe("Failed to insert metrics (SQLite): " + e.getMessage()); }
        } else {
            sendToSupabase("server_metrics", metrics);
        }
    }

    public void insertEventsAsync(String tableName, String sqliteSql, List<Map<String, Object>> events, SqlPstmtSetter setter) {
        if (events.isEmpty()) return;
        CompletableFuture.runAsync(() -> insertEventsSync(tableName, sqliteSql, events, setter));
    }

    public void insertEventsSync(String tableName, String sqliteSql, List<Map<String, Object>> events, SqlPstmtSetter setter) {
        if (events.isEmpty()) return;
        if (type.equals("sqlite")) {
            try (Connection conn = getSQLiteConnection(); PreparedStatement pstmt = conn.prepareStatement(sqliteSql)) {
                conn.setAutoCommit(false);
                for (Map<String, Object> data : events) {
                    setter.setValues(pstmt, data);
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
                conn.commit();
            } catch (SQLException e) { logger.severe("Failed to insert " + tableName + " (SQLite): " + e.getMessage()); }
        } else {
            sendToSupabase(tableName, events);
        }
    }

    private void sendToSupabase(String table, List<Map<String, Object>> payload) {
        if (supabaseUrl == null || supabaseUrl.isEmpty()) return;
        try {
            URL urlObj = new URL(supabaseUrl + "/rest/v1/" + table);
            HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(5000); // 5 seconds timeout
            conn.setReadTimeout(10000);   // 10 seconds timeout
            conn.setRequestProperty("apikey", supabaseKey);
            conn.setRequestProperty("Authorization", "Bearer " + supabaseKey);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = gson.toJson(payload).getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            conn.getResponseCode();
            conn.disconnect();
        } catch (Exception e) {
            logger.severe("Failed to send " + table + " to Supabase: " + e.getMessage());
        }
    }

    @FunctionalInterface
    public interface SqlPstmtSetter {
        void setValues(PreparedStatement pstmt, Map<String, Object> data) throws SQLException;
    }
}
