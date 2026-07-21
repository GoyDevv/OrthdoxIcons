package com.orthodoxicons.model;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A fully-resolved icon that has been downloaded, hashed and persisted. This is
 * the domain object used throughout the plugin and stored in the database.
 */
public final class Icon {

    private final UUID id;
    private final String name;
    private final String saint;
    private final String feast;
    private final String category;
    private final String description;
    private final String sourceUrl;
    private final String imageUrl;
    private final String attribution;
    private final License license;
    private final List<String> tags;
    private final String providerId;
    private final String imageHash;
    private final String etag;
    private final String lastModified;
    private final Instant dateAdded;

    private Icon(Builder b) {
        this.id = Objects.requireNonNull(b.id, "id");
        this.name = Objects.requireNonNull(b.name, "name");
        this.saint = orEmpty(b.saint);
        this.feast = orEmpty(b.feast);
        this.category = b.category == null || b.category.isBlank() ? "Uncategorized" : b.category;
        this.description = orEmpty(b.description);
        this.sourceUrl = orEmpty(b.sourceUrl);
        this.imageUrl = Objects.requireNonNull(b.imageUrl, "imageUrl");
        this.attribution = orEmpty(b.attribution);
        this.license = b.license == null ? new License("Unknown", false) : b.license;
        this.tags = b.tags == null ? Collections.emptyList() : List.copyOf(b.tags);
        this.providerId = Objects.requireNonNull(b.providerId, "providerId");
        this.imageHash = orEmpty(b.imageHash);
        this.etag = b.etag;
        this.lastModified = b.lastModified;
        this.dateAdded = b.dateAdded == null ? Instant.now() : b.dateAdded;
    }

    private static String orEmpty(String v) {
        return v == null ? "" : v;
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public String saint() { return saint; }
    public String feast() { return feast; }
    public String category() { return category; }
    public String description() { return description; }
    public String sourceUrl() { return sourceUrl; }
    public String imageUrl() { return imageUrl; }
    public String attribution() { return attribution; }
    public License license() { return license; }
    public List<String> tags() { return tags; }
    public String providerId() { return providerId; }
    public String imageHash() { return imageHash; }
    public String etag() { return etag; }
    public String lastModified() { return lastModified; }
    public Instant dateAdded() { return dateAdded; }

    /** @return the comma-joined tag list, for storage and display. */
    public String tagsJoined() {
        return String.join(",", tags);
    }

    /** @return a builder pre-populated from this icon (for immutable updates). */
    public Builder toBuilder() {
        return new Builder()
                .id(id).name(name).saint(saint).feast(feast).category(category)
                .description(description).sourceUrl(sourceUrl).imageUrl(imageUrl)
                .attribution(attribution).license(license).tags(tags)
                .providerId(providerId).imageHash(imageHash).etag(etag)
                .lastModified(lastModified).dateAdded(dateAdded);
    }

    /** @return a new builder. */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Icon other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /** Builder for {@link Icon}. */
    public static final class Builder {
        private UUID id;
        private String name;
        private String saint;
        private String feast;
        private String category;
        private String description;
        private String sourceUrl;
        private String imageUrl;
        private String attribution;
        private License license;
        private List<String> tags;
        private String providerId;
        private String imageHash;
        private String etag;
        private String lastModified;
        private Instant dateAdded;

        public Builder id(UUID v) { this.id = v; return this; }
        public Builder name(String v) { this.name = v; return this; }
        public Builder saint(String v) { this.saint = v; return this; }
        public Builder feast(String v) { this.feast = v; return this; }
        public Builder category(String v) { this.category = v; return this; }
        public Builder description(String v) { this.description = v; return this; }
        public Builder sourceUrl(String v) { this.sourceUrl = v; return this; }
        public Builder imageUrl(String v) { this.imageUrl = v; return this; }
        public Builder attribution(String v) { this.attribution = v; return this; }
        public Builder license(License v) { this.license = v; return this; }
        public Builder tags(List<String> v) { this.tags = v; return this; }
        public Builder providerId(String v) { this.providerId = v; return this; }
        public Builder imageHash(String v) { this.imageHash = v; return this; }
        public Builder etag(String v) { this.etag = v; return this; }
        public Builder lastModified(String v) { this.lastModified = v; return this; }
        public Builder dateAdded(Instant v) { this.dateAdded = v; return this; }

        /**
         * Populates identity + descriptive fields from provider metadata.
         *
         * @param meta provider metadata
         * @return this builder
         */
        public Builder fromMetadata(IconMetadata meta) {
            return name(meta.name()).saint(meta.saint()).feast(meta.feast())
                    .category(meta.category()).description(meta.description())
                    .sourceUrl(meta.sourceUrl()).imageUrl(meta.imageUrl())
                    .attribution(meta.attribution()).license(meta.license())
                    .tags(meta.tags()).providerId(meta.providerId());
        }

        /** @return the built {@link Icon}. */
        public Icon build() {
            return new Icon(this);
        }
    }
}
