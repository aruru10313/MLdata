package com.aruru.mldata;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

public class DataCollector {
    private final DatabaseManager dbManager;
    
    private final ConcurrentLinkedQueue<Map<String, Object>> movementQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Map<String, Object>> blockQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Map<String, Object>> deathQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Map<String, Object>> redstoneQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Map<String, Object>> interactionQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Map<String, Object>> damageQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Map<String, Object>> metricQueue = new ConcurrentLinkedQueue<>();
    
    public DataCollector(JavaPlugin plugin, DatabaseManager dbManager, int batchIntervalSeconds) {
        this.dbManager = dbManager;
        // Start async batch task to flush queues to SQLite safely
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::processQueues, 20L * batchIntervalSeconds, 20L * batchIntervalSeconds);
    }
    
    public void logMovement(Map<String, Object> data) { movementQueue.add(data); }
    public void logBlock(Map<String, Object> data) { blockQueue.add(data); }
    public void logDeath(Map<String, Object> data) { deathQueue.add(data); }
    public void logRedstone(Map<String, Object> data) { redstoneQueue.add(data); }
    public void logInteraction(Map<String, Object> data) { interactionQueue.add(data); }
    public void logDamage(Map<String, Object> data) { damageQueue.add(data); }
    public void logMetric(Map<String, Object> data) { metricQueue.add(data); }
    
    private void processQueues() {
        if (!metricQueue.isEmpty()) {
            List<Map<String, Object>> batch = extractBatch(metricQueue);
            dbManager.insertMetricsAsync(batch);
        }
        
        if (!movementQueue.isEmpty()) {
            List<Map<String, Object>> batch = extractBatch(movementQueue);
            dbManager.insertEventsAsync("player_movement", 
                "INSERT INTO player_movement (player_name, player_uuid, world, x, y, z, yaw, pitch, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", 
                batch, (pstmt, data) -> {
                    pstmt.setString(1, (String) data.get("player_name"));
                    pstmt.setString(2, (String) data.get("player_uuid"));
                    pstmt.setString(3, (String) data.get("world"));
                    pstmt.setDouble(4, (Double) data.get("x"));
                    pstmt.setDouble(5, (Double) data.get("y"));
                    pstmt.setDouble(6, (Double) data.get("z"));
                    pstmt.setFloat(7, (Float) data.get("yaw"));
                    pstmt.setFloat(8, (Float) data.get("pitch"));
                    pstmt.setLong(9, (Long) data.get("timestamp"));
                });
        }
        
        if (!blockQueue.isEmpty()) {
            List<Map<String, Object>> batch = extractBatch(blockQueue);
            dbManager.insertEventsAsync("block_events", 
                "INSERT INTO block_events (action, player_name, block_type, world, x, y, z, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", 
                batch, (pstmt, data) -> {
                    pstmt.setString(1, (String) data.get("action"));
                    pstmt.setString(2, (String) data.get("player_name"));
                    pstmt.setString(3, (String) data.get("block_type"));
                    pstmt.setString(4, (String) data.get("world"));
                    pstmt.setInt(5, (Integer) data.get("x"));
                    pstmt.setInt(6, (Integer) data.get("y"));
                    pstmt.setInt(7, (Integer) data.get("z"));
                    pstmt.setLong(8, (Long) data.get("timestamp"));
                });
        }
        
        if (!deathQueue.isEmpty()) {
            List<Map<String, Object>> batch = extractBatch(deathQueue);
            dbManager.insertEventsAsync("death_events", 
                "INSERT INTO death_events (player_name, death_message, cause, world, x, y, z, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", 
                batch, (pstmt, data) -> {
                    pstmt.setString(1, (String) data.get("player_name"));
                    pstmt.setString(2, (String) data.get("death_message"));
                    pstmt.setString(3, (String) data.get("cause"));
                    pstmt.setString(4, (String) data.get("world"));
                    pstmt.setDouble(5, (Double) data.get("x"));
                    pstmt.setDouble(6, (Double) data.get("y"));
                    pstmt.setDouble(7, (Double) data.get("z"));
                    pstmt.setLong(8, (Long) data.get("timestamp"));
                });
        }
        
        if (!redstoneQueue.isEmpty()) {
            List<Map<String, Object>> batch = extractBatch(redstoneQueue);
            dbManager.insertEventsAsync("redstone_events", 
                "INSERT INTO redstone_events (block_type, world, x, y, z, old_current, new_current, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", 
                batch, (pstmt, data) -> {
                    pstmt.setString(1, (String) data.get("block_type"));
                    pstmt.setString(2, (String) data.get("world"));
                    pstmt.setInt(3, (Integer) data.get("x"));
                    pstmt.setInt(4, (Integer) data.get("y"));
                    pstmt.setInt(5, (Integer) data.get("z"));
                    pstmt.setInt(6, (Integer) data.get("old_current"));
                    pstmt.setInt(7, (Integer) data.get("new_current"));
                    pstmt.setLong(8, (Long) data.get("timestamp"));
                });
        }
        
        if (!interactionQueue.isEmpty()) {
            List<Map<String, Object>> batch = extractBatch(interactionQueue);
            dbManager.insertEventsAsync("interaction_events", 
                "INSERT INTO interaction_events (player_name, target_type, target_name, action, world, x, y, z, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", 
                batch, (pstmt, data) -> {
                    pstmt.setString(1, (String) data.get("player_name"));
                    pstmt.setString(2, (String) data.get("target_type"));
                    pstmt.setString(3, (String) data.get("target_name"));
                    pstmt.setString(4, (String) data.get("action"));
                    pstmt.setString(5, (String) data.get("world"));
                    pstmt.setDouble(6, (Double) data.get("x"));
                    pstmt.setDouble(7, (Double) data.get("y"));
                    pstmt.setDouble(8, (Double) data.get("z"));
                    pstmt.setLong(9, (Long) data.get("timestamp"));
                });
        }
        
        if (!damageQueue.isEmpty()) {
            List<Map<String, Object>> batch = extractBatch(damageQueue);
            dbManager.insertEventsAsync("damage_events", 
                "INSERT INTO damage_events (attacker_name, victim_name, damage, cause, world, x, y, z, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", 
                batch, (pstmt, data) -> {
                    pstmt.setString(1, (String) data.get("attacker_name"));
                    pstmt.setString(2, (String) data.get("victim_name"));
                    pstmt.setDouble(3, (Double) data.get("damage"));
                    pstmt.setString(4, (String) data.get("cause"));
                    pstmt.setString(5, (String) data.get("world"));
                    pstmt.setDouble(6, (Double) data.get("x"));
                    pstmt.setDouble(7, (Double) data.get("y"));
                    pstmt.setDouble(8, (Double) data.get("z"));
                    pstmt.setLong(9, (Long) data.get("timestamp"));
                });
        }
    }
    
    private List<Map<String, Object>> extractBatch(ConcurrentLinkedQueue<Map<String, Object>> queue) {
        List<Map<String, Object>> batch = new ArrayList<>();
        // Prevent huge payloads tying up SQLite transactions
        while (!queue.isEmpty() && batch.size() < 5000) {
            batch.add(queue.poll());
        }
        return batch;
    }
    
    public void flushAll() {
        processQueues(); // Flushes remaining data on shutdown
    }
}
