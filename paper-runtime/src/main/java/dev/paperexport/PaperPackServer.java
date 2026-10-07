package dev.paperexport;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Serves only the generated pack, never a directory or a caller-supplied file. */
public final class PaperPackServer implements AutoCloseable {
    private record Snapshot(String hash, byte[] bytes) {}
    private final HttpServer server;
    private final ExecutorService executor;
    private volatile Snapshot snapshot;

    public PaperPackServer(String bind, int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(bind, port), 16);
        executor = Executors.newFixedThreadPool(2, task -> {
            Thread thread = new Thread(task, "PaperExport-pack");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.createContext("/", this::serve);
        server.start();
    }
    public int port() { return server.getAddress().getPort(); }
    public void update(PaperResourcePackBuilder.Result pack) throws IOException {
        snapshot = new Snapshot(pack.sha1(), Files.readAllBytes(pack.path()));
    }
    public String path() {
        Snapshot current = snapshot;
        return current == null ? "" : "/" + current.hash + "/resourcepack.zip";
    }
    private void serve(HttpExchange exchange) throws IOException {
        try {
            Snapshot current = snapshot;
            String method = exchange.getRequestMethod();
            if (!method.equals("GET") && !method.equals("HEAD")) {
                exchange.getResponseHeaders().set("Allow", "GET, HEAD");
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            if (current == null || !exchange.getRequestURI().getPath().equals("/" + current.hash + "/resourcepack.zip")) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            var headers = exchange.getResponseHeaders();
            headers.set("Content-Type", "application/zip");
            headers.set("Cache-Control", "public, max-age=31536000, immutable");
            headers.set("ETag", "\"" + current.hash + "\"");
            headers.set("Content-Length", String.valueOf(current.bytes.length));
            headers.set("X-Content-Type-Options", "nosniff");
            exchange.sendResponseHeaders(200, method.equals("HEAD") ? -1 : current.bytes.length);
            if (method.equals("GET")) exchange.getResponseBody().write(current.bytes);
        } finally { exchange.close(); }
    }
    @Override public void close() { server.stop(0); executor.shutdownNow(); }
}
