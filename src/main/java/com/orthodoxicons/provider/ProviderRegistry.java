package com.orthodoxicons.provider;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Open registry of {@link IconProvider}s. Third-party code or future providers
 * register instances here without touching existing classes, satisfying the
 * Open/Closed Principle.
 */
public final class ProviderRegistry {

    private final ConcurrentHashMap<String, IconProvider> providers = new ConcurrentHashMap<>();

    /**
     * Registers (or replaces) a provider by its id.
     *
     * @param provider the provider to register
     */
    public void register(IconProvider provider) {
        providers.put(provider.id(), provider);
    }

    /**
     * Removes all registered providers (used on reload before re-registering).
     */
    public void clear() {
        providers.clear();
    }

    /**
     * @param id provider id
     * @return the provider, if registered
     */
    public Optional<IconProvider> get(String id) {
        return Optional.ofNullable(providers.get(id));
    }

    /**
     * @return all registered providers
     */
    public Collection<IconProvider> all() {
        return providers.values();
    }

    /**
     * @return only the currently-enabled providers
     */
    public List<IconProvider> enabled() {
        return providers.values().stream()
                .filter(IconProvider::isEnabled)
                .collect(Collectors.toList());
    }

    /**
     * @return the number of registered providers
     */
    public int size() {
        return providers.size();
    }
}
