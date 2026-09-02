package com.aruru.mldata;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public class EmbeddedWebServer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private HttpServer server;
    private final int port;

    public EmbeddedWebServer(int port) {
        this.port = port;
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", new RootHandler());
            server.createContext("/status", new StatusHandler());
            server.createContext("/api/status", new StatusHandler());
            server.setExecutor(null); // creates a default executor
            server.start();
            LOGGER.info("[MLDataCollector] Embedded web server started on port " + port);
        } catch (IOException e) {
            LOGGER.error("[MLDataCollector] Failed to start embedded web server on port {}: {}", port, e.getMessage(), e);
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

    static class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (!"/".equals(path)) {
                String notFound = "{\"error\": \"not_found\", \"path\": \"" + path + "\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                byte[] nfBytes = notFound.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(404, nfBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(nfBytes);
                }
                return;
            }
            String html = "<!doctype html><html><head><meta charset=\"utf-8\"><title>MLDataCollector</title></head>"
                    + "<body style=\"font-family:sans-serif;padding:24px\">"
                    + "<h1>MLDataCollector</h1>"
                    + "<p>Status: running (NeoForge 1.21.1, MySQL)</p>"
                    + "<ul><li><a href=\"/status\">/status</a> - JSON status</li>"
                    + "<li><a href=\"/api/status\">/api/status</a> - alias</li></ul>"
                    + "<p>Port: 8974</p></body></html>";
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}
