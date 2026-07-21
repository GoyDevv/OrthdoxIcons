package com.orthodoxicons.http;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal, dependency-free {@code robots.txt} checker. Fetches and caches the
 * robots policy per host and evaluates {@code Disallow}/{@code Allow} rules for
 * the configured user-agent (falling back to the {@code *} group). Fails open
 * only when the robots file itself cannot be retrieved, and fails closed on an
 * explicit disallow.
 */
public final class RobotsTxtChecker {

    private final HttpClientFactory factory;
    private final ConcurrentHashMap<String, HostRules> cache = new ConcurrentHashMap<>();

    /**
     * @param factory shared HTTP client factory
     */
    public RobotsTxtChecker(HttpClientFactory factory) {
        this.factory = factory;
    }

    /**
     * Clears the cached robots policies (called on reload).
     */
    public void clear() {
        cache.clear();
    }

    /**
     * @param url       the target URL
     * @param userAgent the crawler user-agent
     * @return {@code true} if fetching {@code url} is permitted
     */
    public boolean isAllowed(String url, String userAgent) {
        try {
            URI uri = URI.create(url);
            String host = uri.getScheme() + "://" + uri.getAuthority();
            HostRules rules = cache.computeIfAbsent(host, h -> fetchRules(h, userAgent));
            return rules.isAllowed(uri.getRawPath() == null ? "/" : uri.getRawPath());
        } catch (RuntimeException e) {
            // Malformed URL - let the caller's own request fail naturally.
            return true;
        }
    }

    private HostRules fetchRules(String host, String userAgent) {
        try {
            HttpClient client = factory.client();
            HttpRequest request = HttpRequest.newBuilder(URI.create(host + "/robots.txt"))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", userAgent)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return HostRules.allowAll();
            }
            return HostRules.parse(response.body(), userAgent);
        } catch (Exception e) {
            // If robots.txt cannot be fetched, default to permissive.
            return HostRules.allowAll();
        }
    }

    /**
     * Parsed disallow/allow rules for a single host.
     */
    private static final class HostRules {
        private final List<String> disallow;
        private final List<String> allow;

        private HostRules(List<String> disallow, List<String> allow) {
            this.disallow = disallow;
            this.allow = allow;
        }

        static HostRules allowAll() {
            return new HostRules(List.of(), List.of());
        }

        static HostRules parse(String body, String userAgent) {
            String agentToken = userAgent.split("/")[0].toLowerCase(Locale.ROOT);
            List<String> starDisallow = new ArrayList<>();
            List<String> starAllow = new ArrayList<>();
            List<String> uaDisallow = new ArrayList<>();
            List<String> uaAllow = new ArrayList<>();
            boolean inStar = false;
            boolean inUa = false;
            for (String rawLine : body.split("\n")) {
                String line = stripComment(rawLine).trim();
                if (line.isEmpty()) {
                    continue;
                }
                int colon = line.indexOf(':');
                if (colon < 0) {
                    continue;
                }
                String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                String value = line.substring(colon + 1).trim();
                switch (key) {
                    case "user-agent" -> {
                        String agent = value.toLowerCase(Locale.ROOT);
                        inStar = agent.equals("*");
                        inUa = agent.contains(agentToken) && !agentToken.isBlank();
                    }
                    case "disallow" -> {
                        if (inUa) uaDisallow.add(value);
                        else if (inStar) starDisallow.add(value);
                    }
                    case "allow" -> {
                        if (inUa) uaAllow.add(value);
                        else if (inStar) starAllow.add(value);
                    }
                    default -> { /* ignore Sitemap, Crawl-delay, etc. */ }
                }
            }
            // Prefer user-agent-specific rules when present.
            if (!uaDisallow.isEmpty() || !uaAllow.isEmpty()) {
                return new HostRules(uaDisallow, uaAllow);
            }
            return new HostRules(starDisallow, starAllow);
        }

        private static String stripComment(String line) {
            int hash = line.indexOf('#');
            return hash >= 0 ? line.substring(0, hash) : line;
        }

        boolean isAllowed(String path) {
            // Longest-match wins between Allow and Disallow (per the spec).
            int allowMatch = longestMatch(allow, path);
            int disallowMatch = longestMatch(disallow, path);
            if (disallowMatch < 0) {
                return true;
            }
            return allowMatch >= disallowMatch;
        }

        private static int longestMatch(List<String> rules, String path) {
            int best = -1;
            for (String rule : rules) {
                if (rule.isEmpty()) {
                    continue;
                }
                if (path.startsWith(rule) && rule.length() > best) {
                    best = rule.length();
                }
            }
            return best;
        }
    }
}
