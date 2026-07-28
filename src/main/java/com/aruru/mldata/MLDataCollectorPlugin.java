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
        getLogger().info("서버가 종료됩니다. 남은 데이터를 데이터베이스에 안전하게 저장합니다...");
        if (dataCollector != null) {
            dataCollector.flushAll(); 
        }
        getLogger().info("모든 데이터가 성공적으로 저장되었습니다. MLDataCollector has been disabled.");
    }
}
