package com.aruru.mldata;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

public class EmbeddedWebServer {
    private static final Logger LOGGER = Logger.getLogger("MLDataCollector");
    private HttpServer server;
    private final int port;

    public EmbeddedWebServer(int port) {
        this.port = port;
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/status", new StatusHandler());
            server.setExecutor(null); // creates a default executor
            server.start();
            LOGGER.info("[MLDataCollector] Embedded web server started on port " + port);
        } catch (IOException e) {
            LOGGER.severe("[MLDataCollector] Failed to start embedded web server on port " + port + ": " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            LOGGER.info("[MLDataCollector] Embedded web server stopped.");
        }
    }

    static class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = "{\"status\": \"running\", \"mod\": \"MLDataCollector\", \"version\": \"1.0-SNAPSHOT\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}
