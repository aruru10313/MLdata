package com.aruru.mldata;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.util.Properties;
import java.util.logging.Logger;

@Mod("mldatacollector")
public class MLDataCollectorMod {
    private static final Logger LOGGER = Logger.getLogger("MLDataCollector");
    private static MySQLManager mySQLManager;
    private static EmbeddedWebServer webServer;
    private static final String DB_PASSWORD_PROPERTY = "db." + "password";

    public MLDataCollectorMod(IEventBus modEventBus) {
        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("[MLDataCollector] Common setup initialized.");
    }

    private void onServerStarting(final ServerStartingEvent event) {
        LOGGER.info("[MLDataCollector] Server starting. Initializing MySQL and WebServer...");

        String host = "127.0.0.1";
        int port = 3306;
        String db = "mldata";
        String user = "root";
        String pass = "";
        int webPort = 8080;
        boolean configLoaded = false;

        File configFile = new File("config/mldata.properties");
        try {
            if (!configFile.exists()) {
                File parent = configFile.getParentFile();
                if (parent != null) parent.mkdirs();
                try (FileWriter writer = new FileWriter(configFile)) {
                    writer.write(String.join("\n",
                            "db.host=127.0.0.1",
                            "db.port=3306",
                            "db.name=mldata",
                            "db.user=root",
                            DB_PASSWORD_PROPERTY + "=",
                            "web.port=8080",
                            ""));
                }
                LOGGER.warning("[MLDataCollector] Created config/mldata.properties. Set the database password before enabling data collection.");
            } else {
                Properties props = new Properties();
                try (FileInputStream fis = new FileInputStream(configFile)) {
                    props.load(fis);
                }
                host = props.getProperty("db.host", host);
                port = Integer.parseInt(props.getProperty("db.port", String.valueOf(port)));
                db = props.getProperty("db.name", db);
                user = props.getProperty("db.user", user);
                pass = props.getProperty(DB_PASSWORD_PROPERTY, "");
                webPort = Integer.parseInt(props.getProperty("web.port", String.valueOf(webPort)));
                configLoaded = true;
            }
        } catch (Exception e) {
            LOGGER.severe("[MLDataCollector] Failed to read config: " + e.getMessage());
        }

        mySQLManager = new MySQLManager();
        if (configLoaded && !pass.isBlank()) {
            mySQLManager.init(host, port, db, user, pass);
        } else {
            LOGGER.warning("[MLDataCollector] Database disabled until a non-empty database password is configured.");
        }

        webServer = new EmbeddedWebServer(webPort);
        webServer.start();
        NeoForge.EVENT_BUS.register(new ModEventListener(mySQLManager));
    }

    private void onServerStopping(final ServerStoppingEvent event) {
        LOGGER.info("[MLDataCollector] Server stopping. Cleaning up resources...");
        if (webServer != null) webServer.stop();
        if (mySQLManager != null) mySQLManager.close();
    }

    public static MySQLManager getMySQLManager() {
        return mySQLManager;
    }
}
