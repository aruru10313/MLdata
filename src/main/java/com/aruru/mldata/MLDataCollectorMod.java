package com.aruru.mldata;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

@Mod("mldatacollector")
public class MLDataCollectorMod {
    private static final Logger LOGGER = LogUtils.getLogger();
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

        // Initialize MySQL (Default local or configurable parameters)
        mySQLManager = new MySQLManager();
        mySQLManager.init("localhost", 3306, "mldata", "root", "password");

        // Initialize Embedded Web Server on port 8974 (with DB access for /api/* endpoints)
        webServer = new EmbeddedWebServer(8974, mySQLManager);
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
