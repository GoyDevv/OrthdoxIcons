package com.orthodoxicons.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable metadata describing an icon as reported by a provider, before it is
 * downloaded and assigned a stable UUID. Built with {@link Builder}.
 */
public final class IconMetadata {

    private final String name;
    private final String saint;
    private final String feast;
    private final String category;
    private final String description;
    private final String imageUrl;
    private final String sourceUrl;
    private final String attribution;
    private final License license;
    private final List<String> tags;
    private final String providerId;

    private IconMetadata(Builder b) {
        this.name = Objects.requireNonNull(b.name, "name");
        this.saint = orEmpty(b.saint);
        this.feast = orEmpty(b.feast);
        this.category = b.category == null || b.category.isBlank() ? "Uncategorized" : b.category;
        this.description = orEmpty(b.description);
        this.imageUrl = Objects.requireNonNull(b.imageUrl, "imageUrl");
        this.sourceUrl = orEmpty(b.sourceUrl);
        this.attribution = orEmpty(b.attribution);
        this.license = b.license == null ? new License("Unknown", false) : b.license;
        this.tags = b.tags == null ? Collections.emptyList() : List.copyOf(b.tags);
        this.providerId = Objects.requireNonNull(b.providerId, "providerId");
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    public String name() { return name; }
    public String saint() { return saint; }
    public String feast() { return feast; }
    public String category() { return category; }
    public String description() { return description; }
    public String imageUrl() { return imageUrl; }
    public String sourceUrl() { return sourceUrl; }
    public String attribution() { return attribution; }
    public License license() { return license; }
    public List<String> tags() { return tags; }
    public String providerId() { return providerId; }

    /** @return a new {@link Builder}. */
    public static Builder builder() {
        return new Builder();
    }

    /** Builder for {@link IconMetadata}. */
    public static final class Builder {
        private String name;
        private String saint;
        private String feast;
        private String category;
        private String description;
        private String imageUrl;
        private String sourceUrl;
        private String attribution;
        private License license;
        private List<String> tags;
        private String providerId;

        public Builder name(String v) { this.name = v; return this; }
        public Builder saint(String v) { this.saint = v; return this; }
        public Builder feast(String v) { this.feast = v; return this; }
        public Builder category(String v) { this.category = v; return this; }
        public Builder description(String v) { this.description = v; return this; }
        public Builder imageUrl(String v) { this.imageUrl = v; return this; }
        public Builder sourceUrl(String v) { this.sourceUrl = v; return this; }
        public Builder attribution(String v) { this.attribution = v; return this; }
        public Builder license(License v) { this.license = v; return this; }
        public Builder tags(List<String> v) { this.tags = v; return this; }
        public Builder providerId(String v) { this.providerId = v; return this; }

        /** @return a validated immutable {@link IconMetadata}. */
        public IconMetadata build() {
            return new IconMetadata(this);
        }
    }
}
