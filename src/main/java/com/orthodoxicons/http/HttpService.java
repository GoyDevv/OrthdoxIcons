package com.orthodoxicons.http;

import com.orthodoxicons.config.ConfigManager;
import com.orthodoxicons.config.PluginConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * High-level HTTP helper providing retry with exponential backoff, conditional
 * GETs (ETag / Last-Modified), a global concurrency limiter and text/binary
 * fetch helpers. Never invoked from the main thread.
 */
public final class HttpService {

    private final HttpClientFactory factory;
    private final ConfigManager config;
    private final RobotsTxtChecker robots;
    private final Logger logger;
    private final Semaphore downloadLimiter;

    /**
     * @param factory HTTP client factory
     * @param config  configuration manager
     * @param robots  robots.txt checker
     * @param logger  plugin logger
     */
    public HttpService(HttpClientFactory factory, ConfigManager config,
                       RobotsTxtChecker robots, Logger logger) {
        this.factory = factory;
        this.config = config;
        this.robots = robots;
        this.logger = logger;
        this.downloadLimiter = new Semaphore(config.get().maxConcurrentDownloads(), true);
    }

    /**
     * Performs a plain GET returning the body as UTF-8 text, honoring robots.txt
     * and retrying transient failures.
     *
     * @param url the URL to fetch
     * @return the body text
     * @throws IOException if the request ultimately fails or is disallowed
     */
    public String getText(String url) throws IOException {
        ensureAllowed(url);
        HttpResponse<String> response = executeWithRetry(builderFor(url).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int code = response.statusCode();
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + " for " + url);
        }
        return response.body();
    }

    /**
     * Performs a conditional GET for a binary resource. When the server responds
     * {@code 304 Not Modified} the returned result carries no body, signalling
     * the caller to keep its cached copy.
     *
     * @param url          the resource URL
     * @param etag         previous ETag (nullable)
     * @param lastModified previous Last-Modified (nullable)
     * @return a {@link ConditionalResult}
     * @throws IOException if the request ultimately fails or is disallowed
     */
    public ConditionalResult getBinaryConditional(String url, String etag, String lastModified)
            throws IOException {
        ensureAllowed(url);
        HttpRequest.Builder builder = builderFor(url).GET();
        if (etag != null && !etag.isBlank()) {
            builder.header("If-None-Match", etag);
        }
        if (lastModified != null && !lastModified.isBlank()) {
            builder.header("If-Modified-Since", lastModified);
        }
        downloadLimiter.acquireUninterruptibly();
        try {
            HttpResponse<byte[]> response = executeWithRetry(builder.build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            int code = response.statusCode();
            if (code == 304) {
                return ConditionalResult.notModified();
            }
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code + " for " + url);
            }
            String newEtag = response.headers().firstValue("ETag").orElse(null);
            String newLastMod = response.headers().firstValue("Last-Modified").orElse(null);
            return ConditionalResult.modified(response.body(), newEtag, newLastMod);
        } finally {
            downloadLimiter.release();
        }
    }

    private void ensureAllowed(String url) throws IOException {
        PluginConfig cfg = config.get();
        if (cfg.respectRobots() && !robots.isAllowed(url, cfg.userAgent())) {
            throw new IOException("Blocked by robots.txt: " + url);
        }
    }

    private HttpRequest.Builder builderFor(String url) {
        PluginConfig cfg = config.get();
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(cfg.timeoutSeconds()))
                .header("User-Agent", cfg.userAgent())
                .header("Accept", "*/*");
    }

    private <T> HttpResponse<T> executeWithRetry(HttpRequest request,
                                                 HttpResponse.BodyHandler<T> handler)
            throws IOException {
        PluginConfig cfg = config.get();
        HttpClient http = factory.client();
        int attempts = cfg.retryLimit() + 1;
        IOException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                HttpResponse<T> response = http.send(request, handler);
                int code = response.statusCode();
                // Retry on transient server-side/rate-limit codes only.
                if (code == 429 || (code >= 500 && code < 600)) {
                    last = new IOException("Transient HTTP " + code);
                    backoff(cfg, attempt);
                    continue;
                }
                return response;
            } catch (IOException e) {
                last = e;
                if (config.debug()) {
                    logger.log(Level.FINE, "HTTP attempt " + attempt + " failed: " + e.getMessage());
                }
                backoff(cfg, attempt);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while fetching", e);
            }
        }
        throw last != null ? last : new IOException("Request failed with no exception");
    }

    private void backoff(PluginConfig cfg, int attempt) {
        if (cfg.retryBackoffMs() <= 0) {
            return;
        }
        long delay = cfg.retryBackoffMs() * (1L << Math.min(attempt - 1, 6));
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Result of a conditional binary GET.
     */
    public static final class ConditionalResult {
        private final boolean modified;
        private final byte[] body;
        private final String etag;
        private final String lastModified;

        private ConditionalResult(boolean modified, byte[] body, String etag, String lastModified) {
            this.modified = modified;
            this.body = body;
            this.etag = etag;
            this.lastModified = lastModified;
        }

        static ConditionalResult notModified() {
            return new ConditionalResult(false, null, null, null);
        }

        static ConditionalResult modified(byte[] body, String etag, String lastModified) {
            return new ConditionalResult(true, body, etag, lastModified);
        }

        /** @return whether the resource changed (false = keep cached copy) */
        public boolean isModified() { return modified; }
        /** @return the downloaded body (only when modified) */
        public byte[] body() { return body; }
        /** @return the new ETag, if any */
        public Optional<String> etag() { return Optional.ofNullable(etag); }
        /** @return the new Last-Modified value, if any */
        public Optional<String> lastModified() { return Optional.ofNullable(lastModified); }
    }
}
