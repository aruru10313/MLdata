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
    
    private final ConcurrentLinkedQueue<Map<String, Object>> itemQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Map<String, Object>> chatQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Map<String, Object>> sessionQueue = new ConcurrentLinkedQueue<>();
    private final java.util.concurrent.ConcurrentHashMap<String, Map<String, Object>> hopperAggregator = new java.util.concurrent.ConcurrentHashMap<>();
    
    public DataCollector(JavaPlugin plugin, DatabaseManager dbManager, int batchIntervalSeconds) {
        this.dbManager = dbManager;
        // Start async batch task to flush queues to SQLite safely
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> processQueues(false), 20L * batchIntervalSeconds, 20L * batchIntervalSeconds);
    }
    
    public void logMovement(Map<String, Object> data) { movementQueue.add(data); }
    public void logBlock(Map<String, Object> data) { blockQueue.add(data); }
    public void logDeath(Map<String, Object> data) { deathQueue.add(data); }
    public void logRedstone(Map<String, Object> data) { redstoneQueue.add(data); }
    public void logInteraction(Map<String, Object> data) { interactionQueue.add(data); }
    public void logDamage(Map<String, Object> data) { damageQueue.add(data); }
    public void logMetric(Map<String, Object> data) { metricQueue.add(data); }
    
    public void logItem(Map<String, Object> data) { itemQueue.add(data); }
    public void logChat(Map<String, Object> data) { chatQueue.add(data); }
    public void logSession(Map<String, Object> data) { sessionQueue.add(data); }
    
    public void logHopperTransfer(String world, int x, int y, int z, String itemType) {
        String key = world + "_" + x + "_" + y + "_" + z + "_" + itemType;
        hopperAggregator.compute(key, (k, v) -> {
            if (v == null) {
                Map<String, Object> data = new java.util.HashMap<>();
                data.put("action", "HOPPER_TRANSFER");
                data.put("player_name", "HOPPER");
                data.put("item_type", itemType);
                data.put("amount", 1);
                data.put("world", world);
                data.put("x", (double)x);
                data.put("y", (double)y);
                data.put("z", (double)z);
                data.put("timestamp", System.currentTimeMillis());
                return data;
            } else {
                v.put("amount", (int)v.get("amount") + 1);
                v.put("timestamp", System.currentTimeMillis());
                return v;
            }
        });
    }

    private void processQueues(boolean isSync) {
        // Flush hopper aggregations into itemQueue
        if (!hopperAggregator.isEmpty()) {
            java.util.Iterator<Map.Entry<String, Map<String, Object>>> it = hopperAggregator.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Map<String, Object>> entry = it.next();
                itemQueue.add(entry.getValue());
                it.remove();
            }
        }
        if (!metricQueue.isEmpty()) {
            List<Map<String, Object>> batch = extractBatch(metricQueue);
            if (isSync) dbManager.insertMetricsSync(batch);
            else dbManager.insertMetricsAsync(batch);
        }
        
        if (!movementQueue.isEmpty()) {
            List<Map<String, Object>> batch = extractBatch(movementQueue);
            DatabaseManager.SqlPstmtSetter setter = (pstmt, data) -> {
                pstmt.setString(1, (String) data.get("player_name"));
                pstmt.setString(2, (String) data.get("player_uuid"));
                pstmt.setString(3, (String) data.get("world"));
                pstmt.setDouble(4, (Double) data.get("x"));
                pstmt.setDouble(5, (Double) data.get("y"));
                pstmt.setDouble(6, (Double) data.get("z"));
                pstmt.setFloat(7, (Float) data.get("yaw"));
                pstmt.setFloat(8, (Float) data.get("pitch"));
                pstmt.setLong(9, (Long) data.get("timestamp"));
            };
            if (isSync) dbManager.insertEventsSync("player_movement", "INSERT INTO player_movement (player_name, player_uuid, world, x, y, z, yaw, pitch, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", batch, setter);
            else dbManager.insertEventsAsync("player_movement", "INSERT INTO player_movement (player_name, player_uuid, world, x, y, z, yaw, pitch, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", batch, setter);
        }
        
        if (!blockQueue.isEmpty()) {
            List<Map<String, Object>> batch = extractBatch(blockQueue);
            DatabaseManager.SqlPstmtSetter setter = (pstmt, data) -> {
                pstmt.setString(1, (String) data.get("action"));
                pstmt.setString(2, (String) data.get("player_name"));
                pstmt.setString(3, (String) data.get("block_type"));
                pstmt.setString(4, (String) data.get("world"));
                pstmt.setInt(5, (Integer) data.get("x"));
                pstmt.setInt(6, (Integer) data.get("y"));
                pstmt.setInt(7, (Integer) data.get("z"));
                pstmt.setLong(8, (Long) data.get("timestamp"));
            };
            if (isSync) dbManager.insertEventsSync("block_events", "INSERT INTO block_events (action, player_name, block_type, world, x, y, z, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", batch, setter);
            else dbManager.insertEventsAsync("block_events", "INSERT INTO block_events (action, player_name, block_type, world, x, y, z, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", batch, setter);
        }
        
        if (!deathQueue.isEmpty()) {
            List<Map<String, Object>> batch = extractBatch(deathQueue);
            DatabaseManager.SqlPstmtSetter setter = (pstmt, data) -> {
                pstmt.setString(1, (String) data.get("player_name"));
                pstmt.setString(2, (String) data.get("death_message"));
                pstmt.setString(3, (String) data.get("cause"));
                pstmt.setString(4, (String) data.get("world"));
                pstmt.setDouble(5, (Double) data.get("x"));
                pstmt.setDouble(6, (Double) data.get("y"));
                pstmt.setDouble(7, (Double) data.get("z"));
                pstmt.setLong(8, (Long) data.get("timestamp"));
            };
            if (isSync) dbManager.insertEventsSync("death_events", "INSERT INTO death_events (player_name, death_message, cause, world, x, y, z, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", batch, setter);
            else dbManager.insertEventsAsync("death_events", "INSERT INTO death_events (player_name, death_message, cause, world, x, y, z, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", batch, setter);
        }
        
        if (!redstoneQueue.isEmpty()) {
            List<Map<String, Object>> batch = extractBatch(redstoneQueue);
            DatabaseManager.SqlPstmtSetter setter = (pstmt, data) -> {
                pstmt.setString(1, (String) data.get("block_type"));
                pstmt.setString(2, (String) data.get("world"));
                pstmt.setInt(3, (Integer) data.get("x"));
                pstmt.setInt(4, (Integer) data.get("y"));
                pstmt.setInt(5, (Integer) data.get("z"));
                pstmt.setInt(6, (Integer) data.get("old_current"));
                pstmt.setInt(7, (Integer) data.get("new_current"));
                pstmt.setLong(8, (Long) data.get("timestamp"));
            };
            if (isSync) dbManager.insertEventsSync("redstone_events", "INSERT INTO redstone_events (block_type, world, x, y, z, old_current, new_current, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", batch, setter);
            else dbManager.insertEventsAsync("redstone_events", "INSERT INTO redstone_events (block_type, world, x, y, z, old_current, new_current, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", batch, setter);
        }
        
        if (!interactionQueue.isEmpty()) {
            List<Map<String, Object>> batch = extractBatch(interactionQueue);
            DatabaseManager.SqlPstmtSetter setter = (pstmt, data) -> {
                pstmt.setString(1, (String) data.get("player_name"));
                pstmt.setString(2, (String) data.get("target_type"));
                pstmt.setString(3, (String) data.get("target_name"));
                pstmt.setString(4, (String) data.get("action"));
                pstmt.setString(5, (String) data.get("world"));
                pstmt.setDouble(6, (Double) data.get("x"));
                pstmt.setDouble(7, (Double) data.get("y"));
                pstmt.setDouble(8, (Double) data.get("z"));
                pstmt.setLong(9, (Long) data.get("timestamp"));
            };
            if (isSync) dbManager.insertEventsSync("interaction_events", "INSERT INTO interaction_events (player_name, target_type, target_name, action, world, x, y, z, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", batch, setter);
            else dbManager.insertEventsAsync("interaction_events", "INSERT INTO interaction_events (player_name, target_type, target_name, action, world, x, y, z, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", batch, setter);
        }
        
        if (!damageQueue.isEmpty()) {
            List<Map<String, Object>> batch = extractBatch(damageQueue);
            DatabaseManager.SqlPstmtSetter setter = (pstmt, data) -> {
                pstmt.setString(1, (String) data.get("attacker_name"));
                pstmt.setString(2, (String) data.get("victim_name"));
                pstmt.setDouble(3, (Double) data.get("damage"));
                pstmt.setString(4, (String) data.get("cause"));
                pstmt.setString(5, (String) data.get("world"));
                pstmt.setDouble(6, (Double) data.get("x"));
                pstmt.setDouble(7, (Double) data.get("y"));
                pstmt.setDouble(8, (Double) data.get("z"));
                pstmt.setLong(9, (Long) data.get("timestamp"));
            };
            if (isSync) dbManager.insertEventsSync("damage_events", "INSERT INTO damage_events (attacker_name, victim_name, damage, cause, world, x, y, z, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", batch, setter);
            else dbManager.insertEventsAsync("damage_events", "INSERT INTO damage_events (attacker_name, victim_name, damage, cause, world, x, y, z, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", batch, setter);
        }
        
        if (!itemQueue.isEmpty()) {
            List<Map<String, Object>> batch = extractBatch(itemQueue);
            DatabaseManager.SqlPstmtSetter setter = (pstmt, data) -> {
                pstmt.setString(1, (String) data.get("action"));
                pstmt.setString(2, (String) data.get("player_name"));
                pstmt.setString(3, (String) data.get("item_type"));
                pstmt.setInt(4, (Integer) data.get("amount"));
                pstmt.setString(5, (String) data.get("world"));
                pstmt.setDouble(6, (Double) data.get("x"));
                pstmt.setDouble(7, (Double) data.get("y"));
                pstmt.setDouble(8, (Double) data.get("z"));
                pstmt.setLong(9, (Long) data.get("timestamp"));
            };
            if (isSync) dbManager.insertEventsSync("item_events", "INSERT INTO item_events (action, player_name, item_type, amount, world, x, y, z, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", batch, setter);
            else dbManager.insertEventsAsync("item_events", "INSERT INTO item_events (action, player_name, item_type, amount, world, x, y, z, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", batch, setter);
        }
        
        if (!chatQueue.isEmpty()) {
            List<Map<String, Object>> batch = extractBatch(chatQueue);
            DatabaseManager.SqlPstmtSetter setter = (pstmt, data) -> {
                pstmt.setString(1, (String) data.get("player_name"));
                pstmt.setInt(2, (Integer) data.get("is_command"));
                pstmt.setString(3, (String) data.get("message"));
                pstmt.setString(4, (String) data.get("world"));
                pstmt.setDouble(5, (Double) data.get("x"));
                pstmt.setDouble(6, (Double) data.get("y"));
                pstmt.setDouble(7, (Double) data.get("z"));
                pstmt.setLong(8, (Long) data.get("timestamp"));
            };
            if (isSync) dbManager.insertEventsSync("chat_events", "INSERT INTO chat_events (player_name, is_command, message, world, x, y, z, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", batch, setter);
            else dbManager.insertEventsAsync("chat_events", "INSERT INTO chat_events (player_name, is_command, message, world, x, y, z, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", batch, setter);
        }
        
        if (!sessionQueue.isEmpty()) {
            List<Map<String, Object>> batch = extractBatch(sessionQueue);
            DatabaseManager.SqlPstmtSetter setter = (pstmt, data) -> {
                pstmt.setString(1, (String) data.get("player_name"));
                pstmt.setString(2, (String) data.get("action"));
                pstmt.setString(3, (String) data.get("ip_address"));
                pstmt.setLong(4, (Long) data.get("timestamp"));
            };
            if (isSync) dbManager.insertEventsSync("session_events", "INSERT INTO session_events (player_name, action, ip_address, timestamp) VALUES (?, ?, ?, ?)", batch, setter);
            else dbManager.insertEventsAsync("session_events", "INSERT INTO session_events (player_name, action, ip_address, timestamp) VALUES (?, ?, ?, ?)", batch, setter);
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
        processQueues(true); // Flushes remaining data synchronously on shutdown
    }
}
