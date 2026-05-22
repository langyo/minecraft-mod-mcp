package xyz.langyo.minecraft.mcp.common;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

public class McpHttpServer {

    private static final int DEFAULT_PORT = 9876;
    private HttpServer server;
    private final McpMessageHandler handler;
    private final int port;
    private final List<CallEvent> callHistory = new CopyOnWriteArrayList<>();
    private final List<SseClient> sseClients = new CopyOnWriteArrayList<>();
    private static final int MAX_HISTORY = 200;
    private static final Map<String, String> MIME_TYPES = new HashMap<>();
    static {
        MIME_TYPES.put(".html", "text/html; charset=utf-8");
        MIME_TYPES.put(".css", "text/css; charset=utf-8");
        MIME_TYPES.put(".js", "application/javascript; charset=utf-8");
        MIME_TYPES.put(".json", "application/json");
        MIME_TYPES.put(".png", "image/png");
        MIME_TYPES.put(".svg", "image/svg+xml");
    }

    public static class CallEvent {
        public long timestamp;
        public String direction;
        public String method;
        public String params;
        public String result;
        public String error;
        public long durationMs;
    }

    private static class SseClient {
        OutputStream os;
        volatile boolean active = true;
    }

    public McpHttpServer(McpMessageHandler handler, int port) {
        this.handler = handler;
        this.port = port > 0 ? port : DEFAULT_PORT;
    }

    public McpHttpServer(McpMessageHandler handler) {
        this(handler, DEFAULT_PORT);
    }

    public int getPort() { return port; }

    public void logEvent(String method, String params, String result, String error) {
        CallEvent ev = new CallEvent();
        ev.timestamp = System.currentTimeMillis();
        ev.direction = "mod";
        ev.method = method;
        ev.params = params;
        ev.result = result;
        ev.error = error;
        emitEvent(ev);
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.createContext("/api/screenshot", new ScreenshotHandler());
        server.createContext("/api/cmd", new CmdHandler());
        server.createContext("/api/events", new EventHandler());
        server.createContext("/api/calls", new CallsHandler());
        server.createContext("/api/status", exchange -> {
            sendJson(exchange, 200, "{\"ok\":true,\"port\":" + port + "}");
        });
        server.createContext("/debug", new StaticHandler());
        server.createContext("/", new RootHandler());
        server.start();
        ReflectionHelper.dbg("McpHttpServer: started on port " + port);
    }

    public void stop() {
        if (server != null) server.stop(0);
        for (SseClient c : sseClients) c.active = false;
        sseClients.clear();
    }

    private void emitEvent(CallEvent ev) {
        callHistory.add(ev);
        while (callHistory.size() > MAX_HISTORY) callHistory.remove(0);
        broadcastSse(ev);
    }

    private void broadcastSse(CallEvent ev) {
        String data = eventToJson(ev);
        String msg = "data: " + data + "\n\n";
        byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
        Iterator<SseClient> it = sseClients.iterator();
        while (it.hasNext()) {
            SseClient c = it.next();
            try {
                c.os.write(bytes);
                c.os.flush();
            } catch (Exception e) {
                c.active = false;
                sseClients.remove(c);
            }
        }
    }

    private String eventToJson(CallEvent ev) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"timestamp\":").append(ev.timestamp);
        sb.append(",\"direction\":\"").append(esc(ev.direction)).append("\"");
        sb.append(",\"method\":\"").append(esc(ev.method)).append("\"");
        if (ev.params != null) sb.append(",\"params\":").append(ev.params);
        if (ev.result != null) sb.append(",\"result\":").append(ev.result.length() > 500 ? quote(ev.result.substring(0, 500)) : quote(ev.result));
        if (ev.error != null) sb.append(",\"error\":\"").append(esc(ev.error)).append("\"");
        if (ev.durationMs > 0) sb.append(",\"duration_ms\":").append(ev.durationMs);
        sb.append("}");
        return sb.toString();
    }

    private static String esc(String s) { return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n"); }
    private static String quote(String s) { return "\"" + esc(s) + "\""; }

    class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            serveResource(exchange, "index.html");
        }
    }

    class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/") || path.equals("/index.html") || path.equals("/debug")) {
                serveResource(exchange, "index.html");
                return;
            }
            String resource = path.startsWith("/") ? path.substring(1) : path;
            if (resource.startsWith("mcp-debug/")) resource = resource.substring("mcp-debug/".length());
            if (resource.isEmpty()) resource = "index.html";
            if (resource.contains("..")) {
                sendJson(exchange, 403, "{\"error\":\"forbidden\"}");
                return;
            }
            serveResource(exchange, resource);
        }
    }

    private void serveResource(HttpExchange exchange, String name) throws IOException {
        String path = "mcp-debug/" + name;
        InputStream is = McpHttpServer.class.getClassLoader().getResourceAsStream(path);
        if (is == null) {
            sendJson(exchange, 404, "{\"error\":\"not found\"}");
            return;
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
        is.close();
        byte[] data = baos.toByteArray();
        String mime = "application/octet-stream";
        int dot = name.lastIndexOf('.');
        if (dot >= 0) mime = MIME_TYPES.getOrDefault(name.substring(dot).toLowerCase(), mime);
        exchange.getResponseHeaders().set("Content-Type", mime);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, data.length);
        exchange.getResponseBody().write(data);
        exchange.getResponseBody().close();
    }

    class ScreenshotHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            CallEvent ev = new CallEvent();
            ev.timestamp = System.currentTimeMillis();
            ev.direction = "request";
            ev.method = "screenshot";
            long start = System.nanoTime();
            try {
                java.util.Map<String, String> empty = new java.util.LinkedHashMap<>();
                Object result = handler.dispatch("screenshot", empty, null);
                String b64Data = result instanceof String ? (String) result : McpProtocol.GSON.toJson(result);
                if (b64Data == null || !b64Data.startsWith("data:image/png;base64,")) {
                    sendJson(exchange, 500, "{\"error\":\"" + esc(b64Data != null ? b64Data : "null") + "\"}");
                    ev.error = "bad screenshot data";
                    ev.direction = "response";
                    ev.durationMs = (System.nanoTime() - start) / 1_000_000;
                    emitEvent(ev);
                    return;
                }
                String rawB64 = b64Data.substring("data:image/png;base64,".length());
                byte[] pngBytes = Base64.getDecoder().decode(rawB64);
                BufferedImage img = javax.imageio.ImageIO.read(new ByteArrayInputStream(pngBytes));
                int w = img != null ? img.getWidth() : 0;
                int h = img != null ? img.getHeight() : 0;
                String b64Grid = generateGridBase64(pngBytes, w, h);
                String json = "{\"original\":\"" + b64Data + "\",\"grid\":\"data:image/png;base64," + b64Grid + "\",\"width\":" + w + ",\"height\":" + h + "}";
                ev.result = "png " + w + "x" + h;
                ev.durationMs = (System.nanoTime() - start) / 1_000_000;
                ev.direction = "response";
                emitEvent(ev);
                sendJson(exchange, 200, json);
            } catch (Exception e) {
                ev.error = e.getMessage();
                ev.durationMs = (System.nanoTime() - start) / 1_000_000;
                ev.direction = "response";
                emitEvent(ev);
                sendJson(exchange, 500, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
            }
        }
    }

    private String generateGridBase64(byte[] pngBytes, int w, int h) {
        try {
            BufferedImage img = javax.imageio.ImageIO.read(new ByteArrayInputStream(pngBytes));
            if (img == null) return "";
            BufferedImage canvas = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = canvas.createGraphics();
            g.drawImage(img, 0, 0, null);
            g.setColor(new Color(255, 0, 0, 180));
            g.setStroke(new BasicStroke(3));
            int step = 100;
            for (int y = 0; y < h; y += step) { g.drawLine(0, y, w, y); }
            for (int x = 0; x < w; x += step) { g.drawLine(x, 0, x, h); }
            g.setColor(new Color(255, 255, 0, 255));
            g.setFont(new Font("Monospaced", Font.BOLD, 14));
            for (int x = step; x < w; x += step) { g.drawString(String.valueOf(x), x + 4, 16); }
            for (int y = step; y < h; y += step) { g.drawString(String.valueOf(y), 4, y + 14); }
            g.dispose();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(canvas, "png", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }

    class CmdHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "POST");
                sendJson(exchange, 405, "{\"error\":\"method not allowed\"}");
                return;
            }
            String body = readBody(exchange);
            CallEvent ev = new CallEvent();
            ev.timestamp = System.currentTimeMillis();
            ev.direction = "request";
            long start = System.nanoTime();
            String result;
            try {
                result = dispatchCmd(body, ev);
            } catch (Exception e) {
                result = "{\"error\":\"" + esc(e.getMessage()) + "\"}";
                ev.error = e.getMessage();
            }
            ev.durationMs = (System.nanoTime() - start) / 1_000_000;
            ev.result = result;
            ev.direction = "response";
            emitEvent(ev);
            sendJson(exchange, 200, result);
        }

        private String dispatchCmd(String body, CallEvent ev) {
            try {
                com.google.gson.JsonObject jo = McpProtocol.GSON.fromJson(body, com.google.gson.JsonObject.class);
                String cmd = jo.has("cmd") ? jo.get("cmd").getAsString() : jo.has("method") ? jo.get("method").getAsString() : "";
                ev.method = cmd;
                ev.params = body;
                java.util.Map<String, String> params = new java.util.LinkedHashMap<>();
                if (jo.has("params") && jo.get("params").isJsonObject()) {
                    for (java.util.Map.Entry<String, com.google.gson.JsonElement> e : jo.getAsJsonObject("params").entrySet()) {
                        params.put(e.getKey(), e.getValue().isJsonNull() ? "" : e.getValue().getAsString());
                    }
                }
                for (java.util.Map.Entry<String, com.google.gson.JsonElement> e : jo.entrySet()) {
                    if (!e.getKey().equals("cmd") && !e.getKey().equals("method") && !e.getKey().equals("params") && !e.getValue().isJsonObject()) {
                        params.putIfAbsent(e.getKey(), e.getValue().isJsonNull() ? "" : e.getValue().getAsString());
                    }
                }
                Object r = handler.dispatch(cmd, params, null);
                if (r instanceof String) {
                    String s = (String) r;
                    if (s.startsWith("data:image") || s.startsWith("{") || s.startsWith("[")) return s;
                    return "{\"result\":\"" + esc(s) + "\"}";
                }
                return McpProtocol.GSON.toJson(r);
            } catch (Exception e) {
                return "{\"error\":\"" + esc(e.getMessage()) + "\"}";
            }
        }
    }

    class EventHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (sseClients.size() >= 4) {
                sendJson(exchange, 503, "{\"error\":\"too many SSE clients\"}");
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.getResponseHeaders().set("Connection", "keep-alive");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, 0);
            OutputStream os = exchange.getResponseBody();
            SseClient client = new SseClient();
            client.os = os;
            sseClients.add(client);
            int histSize = callHistory.size();
            int start = Math.max(0, histSize - 20);
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < histSize; i++) {
                sb.append("data: ").append(eventToJson(callHistory.get(i))).append("\n\n");
            }
            if (sb.length() > 0) {
                os.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            int timeout = 300;
            while (client.active && timeout > 0) {
                try { Thread.sleep(1000); timeout--; } catch (InterruptedException e) { break; }
            }
            client.active = false;
            sseClients.remove(client);
            os.close();
        }
    }

    class CallsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            StringBuilder sb = new StringBuilder("[");
            int sz = callHistory.size();
            int start = Math.max(0, sz - 50);
            for (int i = start; i < sz; i++) {
                if (i > start) sb.append(",");
                sb.append(eventToJson(callHistory.get(i)));
            }
            sb.append("]");
            sendJson(exchange, 200, sb.toString());
        }
    }

    private static void sendJson(HttpExchange exchange, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(code, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
        return baos.toString(StandardCharsets.UTF_8.name());
    }
}
