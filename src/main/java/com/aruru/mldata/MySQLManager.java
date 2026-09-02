package com.aruru.mldata;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import java.util.logging.Logger;

public class MySQLManager {
    private static final Logger LOGGER = Logger.getLogger("MLDataCollector");
    private HikariDataSource dataSource;

    public void init(String host, int port, String database, String username, String password) {
        HikariConfig config = new HikariConfig();
        String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&characterEncoding=UTF-8", host, port, database);

        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(30000);
        config.setConnectionTimeout(10000);

        try {
            dataSource = new HikariDataSource(config);
            createTables();
            LOGGER.info("[MLDataCollector] MySQL connection established successfully.");
        } catch (Exception e) {
            LOGGER.severe("[MLDataCollector] Failed to connect to MySQL: " + e.getMessage());
        }
    }

    private void createTables() {
        String movementTable = "CREATE TABLE IF NOT EXISTS ml_movements (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "player_name VARCHAR(64), " +
                "player_uuid VARCHAR(36), " +
                "world VARCHAR(64), " +
                "x DOUBLE, y DOUBLE, z DOUBLE, " +
                "yaw FLOAT, pitch FLOAT, " +
                "timestamp BIGINT)";

        String blockTable = "CREATE TABLE IF NOT EXISTS ml_blocks (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "action VARCHAR(16), " +
                "player_name VARCHAR(64), " +
                "block_type VARCHAR(64), " +
                "world VARCHAR(64), " +
                "x INT, y INT, z INT, " +
                "timestamp BIGINT)";

        String chatTable = "CREATE TABLE IF NOT EXISTS ml_chat (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "player_name VARCHAR(64), " +
                "is_command INT, " +
                "message TEXT, " +
                "world VARCHAR(64), " +
                "timestamp BIGINT)";

        try (Connection conn = getConnection()) {
            if (conn != null) {
                try (PreparedStatement stmt1 = conn.prepareStatement(movementTable);
                     PreparedStatement stmt2 = conn.prepareStatement(blockTable);
                     PreparedStatement stmt3 = conn.prepareStatement(chatTable)) {
                    stmt1.execute();
                    stmt2.execute();
                    stmt3.execute();
                }
            }
        } catch (SQLException e) {
            LOGGER.severe("[MLDataCollector] Failed to create tables: " + e.getMessage());
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("DataSource is not initialized.");
        }
        return dataSource.getConnection();
    }

    public void logMovement(Map<String, Object> data) {
        String sql = "INSERT INTO ml_movements (player_name, player_uuid, world, x, y, z, yaw, pitch, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        executeAsync(sql, data.get("player_name"), data.get("player_uuid"), data.get("world"),
                data.get("x"), data.get("y"), data.get("z"), data.get("yaw"), data.get("pitch"), data.get("timestamp"));
    }

    public void logBlock(Map<String, Object> data) {
        String sql = "INSERT INTO ml_blocks (action, player_name, block_type, world, x, y, z, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        executeAsync(sql, data.get("action"), data.get("player_name"), data.get("block_type"),
                data.get("world"), data.get("x"), data.get("y"), data.get("z"), data.get("timestamp"));
    }

    public void logChat(Map<String, Object> data) {
        String sql = "INSERT INTO ml_chat (player_name, is_command, message, world, timestamp) VALUES (?, ?, ?, ?, ?)";
        executeAsync(sql, data.get("player_name"), data.get("is_command"), data.get("message"),
                data.get("world"), data.get("timestamp"));
    }

    private void executeAsync(String sql, Object... params) {
        if (dataSource == null) return;
        new Thread(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }
                stmt.executeUpdate();
            } catch (SQLException e) {
                LOGGER.warning("[MLDataCollector] Async SQL execute failed: " + e.getMessage());
            }
        }).start();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            LOGGER.info("[MLDataCollector] MySQL connection closed.");
        }
    }
}
