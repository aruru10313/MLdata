package com.aruru.mldata;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.logging.Logger;

@Mod("mldatacollector")
public class MLDataCollectorMod {
    private static final Logger LOGGER = Logger.getLogger("MLDataCollector");
    private static MySQLManager mySQLManager;
    private static EmbeddedWebServer webServer;

    public MLDataCollectorMod(IEventBus modEventBus) {
        modEventBus.addListener(this::commonSetup);

        // Register server lifecycle events
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("[MLDataCollector] Common setup initialized.");
    }

    private void onServerStarting(final ServerStartingEvent event) {
        LOGGER.info("[MLDataCollector] Server starting. Initializing MySQL and WebServer...");

        // Security Patch: Read from properties file instead of hardcoded
        String host = "127.0.0.1";
        int port = 3306;
        String db = "mldata";
        String user = "root";
        String pass = "password"; // Default fallback
        
        java.io.File configFile = new java.io.File("config/mldata.properties");
        try {
            if (!configFile.exists()) {
                configFile.getParentFile().mkdirs();
                try (java.io.FileWriter writer = new java.io.FileWriter(configFile)) {
                    writer.write("db.host=127.0.0.1\ndb.port=3306\ndb.name=mldata\ndb.user=root\ndb.password=password\n");
                }
            } else {
                java.util.Properties props = new java.util.Properties();
                try (java.io.FileInputStream fis = new java.io.FileInputStream(configFile)) {
                    props.load(fis);
                    host = props.getProperty("db.host", "127.0.0.1");
                    port = Integer.parseInt(props.getProperty("db.port", "3306"));
                    db = props.getProperty("db.name", "mldata");
                    user = props.getProperty("db.user", "root");
                    pass = props.getProperty("db.password", "password");
                }
            }
        } catch (Exception e) {
            LOGGER.warning("[MLDataCollector] Failed to read config, using defaults.");
        }

        // Initialize MySQL
        mySQLManager = new MySQLManager();
        mySQLManager.init(host, port, db, user, pass);

        // Initialize Embedded Web Server on port 8974
        webServer = new EmbeddedWebServer(8974);
        webServer.start();

        // Register Event Listener for data collection
        NeoForge.EVENT_BUS.register(new ModEventListener(mySQLManager));
    }

    private void onServerStopping(final ServerStoppingEvent event) {
        LOGGER.info("[MLDataCollector] Server stopping. Cleaning up resources...");
        if (webServer != null) {
            webServer.stop();
        }
        if (mySQLManager != null) {
            mySQLManager.close();
        }
    }

    public static MySQLManager getMySQLManager() {
        return mySQLManager;
    }
}
