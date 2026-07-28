package com.aruru.mldata;

import org.bukkit.plugin.java.JavaPlugin;

public class MLDataCollectorPlugin extends JavaPlugin {
    
    private DatabaseManager dbManager;
    private DataCollector dataCollector;
    private WebServerManager webServerManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        
        dbManager = new DatabaseManager(this);
        
        int batchInterval = getConfig().getInt("collection.batch-interval-seconds", 60);
        long movementThrottle = getConfig().getLong("collection.movement-throttle-ms", 2000L);
        int webPort = getConfig().getInt("web.port", 8080);
        String webPassword = getConfig().getString("web.password", "10313");
        
        dataCollector = new DataCollector(this, dbManager, batchInterval);
        webServerManager = new WebServerManager(this, dbManager, webPort, webPassword);
        
        new MetricTask(this, dataCollector, batchInterval);
        getServer().getPluginManager().registerEvents(new EventListener(dataCollector, movementThrottle), this);
        
        // Register In-game commands
        getCommand("mldata").setExecutor(new CommandManager(dataCollector, dbManager, webServerManager));
        
        getLogger().info("MLDataCollector has been enabled! Secure Web Dashboard is active on port " + webPort);
    }

    @Override
    public void onDisable() {
        if (dataCollector != null) {
            dataCollector.flushAll(); 
        }
        if (webServerManager != null) {
            webServerManager.stop();
        }
        getLogger().info("MLDataCollector has been disabled.");
    }
}
