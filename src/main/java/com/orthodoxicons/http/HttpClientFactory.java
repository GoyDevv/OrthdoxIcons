package com.orthodoxicons.http;

import com.orthodoxicons.config.PluginConfig;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds and owns the shared {@link HttpClient}. The JDK client pools and reuses
 * HTTP/2 and keep-alive HTTP/1.1 connections internally, satisfying the
 * connection-pooling requirement without any third-party dependency. All
 * requests run on a dedicated, bounded executor so networking never touches a
 * server thread.
 */
public final class HttpClientFactory implements AutoCloseable {

    private final HttpClient client;
    private final ExecutorService httpExecutor;

    /**
     * @param config current configuration snapshot
     */
    public HttpClientFactory(PluginConfig config) {
        this.httpExecutor = Executors.newFixedThreadPool(
                Math.max(2, config.maxConcurrentDownloads() * 2),
                namedFactory("OrthodoxIcons-HTTP"));
        this.client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(config.timeoutSeconds()))
                .executor(httpExecutor)
                .version(HttpClient.Version.HTTP_2)
                .build();
    }

    /**
     * @return the shared, connection-pooling HTTP client
     */
    public HttpClient client() {
        return client;
    }

    private static ThreadFactory namedFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread t = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    @Override
    public void close() {
        httpExecutor.shutdownNow();
    }
}
