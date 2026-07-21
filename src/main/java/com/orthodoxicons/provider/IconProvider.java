package com.orthodoxicons.provider;

import com.orthodoxicons.model.IconMetadata;

import java.util.List;

/**
 * A source of Orthodox icon metadata. Implementations discover icons and expose
 * their metadata; the fetch service handles downloading, hashing and caching.
 * <p>
 * New providers can be added without modifying existing code: implement this
 * interface and register the instance with the {@link ProviderRegistry}. This
 * is the Provider Pattern combined with an open registry (Open/Closed).
 */
public interface IconProvider {

    /**
     * @return the stable, unique provider id (matches a key under
     *         {@code providers:} in config.yml)
     */
    String id();

    /**
     * @return a human-readable display name
     */
    String displayName();

    /**
     * @return {@code true} if this provider is enabled in the current config
     */
    boolean isEnabled();

    /**
     * Discovers icons and returns their metadata. Implementations must run
     * quickly-cancellable, blocking network I/O here; the caller always invokes
     * this off the main thread.
     *
     * @return discovered icon metadata (never {@code null}; empty on failure)
     * @throws Exception if discovery fails irrecoverably
     */
    List<IconMetadata> discover() throws Exception;
}
