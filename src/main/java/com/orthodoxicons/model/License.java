package com.orthodoxicons.model;

import java.util.Locale;

/**
 * Represents the licensing information attached to an icon. The plugin only
 * accepts icons whose license is considered free for redistribution.
 */
public final class License {

    private final String name;
    private final boolean free;

    /**
     * Creates a license descriptor.
     *
     * @param name human-readable license label (never {@code null})
     * @param free whether the license permits free redistribution
     */
    public License(String name, boolean free) {
        this.name = name == null ? "Unknown" : name;
        this.free = free;
    }

    /**
     * Attempts to classify a license from an arbitrary label. Any label that
     * mentions a public-domain, CC0 or CC-BY style license is treated as free.
     *
     * @param label raw license text (may be {@code null})
     * @return a classified {@link License}
     */
    public static License fromLabel(String label) {
        if (label == null || label.isBlank()) {
            return new License("Unknown", false);
        }
        String normalized = label.toLowerCase(Locale.ROOT);
        boolean free = normalized.contains("public domain")
                || normalized.contains("pd-")
                || normalized.contains("pd ")
                || normalized.contains("cc0")
                || normalized.contains("cc-by")
                || normalized.contains("cc by")
                || normalized.contains("creative commons");
        return new License(label, free);
    }

    /** @return the human-readable license label */
    public String name() {
        return name;
    }

    /** @return {@code true} if the license permits free redistribution */
    public boolean isFree() {
        return free;
    }

    @Override
    public String toString() {
        return name;
    }
}
