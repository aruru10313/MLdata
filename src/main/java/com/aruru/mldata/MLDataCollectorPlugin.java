package com.aruru.mldata;

import org.bukkit.plugin.java.JavaPlugin;

public class MLDataCollectorPlugin extends JavaPlugin {
    
    private DatabaseManager dbManager;
    private DataCollector dataCollector;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        
        dbManager = new DatabaseManager(this);
        
        int batchInterval = getConfig().getInt("collection.batch-interval-seconds", 60);
        long movementThrottle = getConfig().getLong("collection.movement-throttle-ms", 2000L);
        
        dataCollector = new DataCollector(this, dbManager, batchInterval);
        
        new MetricTask(this, dataCollector, batchInterval);
        getServer().getPluginManager().registerEvents(new EventListener(dataCollector, movementThrottle), this);
        
        // Register In-game commands
        getCommand("mldata").setExecutor(new CommandManager(dataCollector, dbManager));
        
        getLogger().info("MLDataCollector has been enabled! Connected to DB.");
    }

    @Override
    public void onDisable() {
        if (dataCollector != null) {
            dataCollector.flushAll(); 
        }
        getLogger().info("MLDataCollector has been disabled.");
    }
}
