package com.aruru.mldata;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public class MetricTask implements Runnable {
    private final DataCollector dataCollector;

    public MetricTask(JavaPlugin plugin, DataCollector dataCollector, int intervalSeconds) {
        this.dataCollector = dataCollector;
        // Schedule synchronously to safely access world/entity data
        Bukkit.getScheduler().runTaskTimer(plugin, this, 20L * intervalSeconds, 20L * intervalSeconds);
    }

    @Override
    public void run() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / 1048576L;
        long allocatedMemory = runtime.totalMemory() / 1048576L;
        long freeMemory = runtime.freeMemory() / 1048576L;
        long usedMemory = allocatedMemory - freeMemory;
        
        double[] tpsArray = Bukkit.getServer().getTPS();
        double tps1m = tpsArray.length > 0 ? tpsArray[0] : 20.0;
        
        int loadedChunks = 0;
        int entityCount = 0;
        
        for (World world : Bukkit.getWorlds()) {
            loadedChunks += world.getLoadedChunks().length;
            entityCount += world.getEntityCount();
        }
        
        Map<String, Object> data = new HashMap<>();
        data.put("tps", Math.min(Math.round(tps1m * 100.0) / 100.0, 20.0));
        data.put("ram_used_mb", usedMemory);
        data.put("ram_max_mb", maxMemory);
        data.put("loaded_chunks", loadedChunks);
        data.put("entity_count", entityCount);
        data.put("online_players", Bukkit.getOnlinePlayers().size());
        data.put("timestamp", System.currentTimeMillis());
        
        dataCollector.logMetric(data);
    }
}
