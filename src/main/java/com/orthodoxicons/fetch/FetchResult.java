package com.orthodoxicons.fetch;

/**
 * Aggregated outcome of a fetch/update pass across all enabled providers.
 * Immutable value object.
 */
public final class FetchResult {

    private final int added;
    private final int updated;
    private final int skipped;
    private final int failed;

    /**
     * @param added   number of brand-new icons persisted
     * @param updated number of existing icons whose image changed
     * @param skipped number of icons unchanged (not re-downloaded)
     * @param failed  number of icons that failed after all retries
     */
    public FetchResult(int added, int updated, int skipped, int failed) {
        this.added = added;
        this.updated = updated;
        this.skipped = skipped;
        this.failed = failed;
    }

    /** @return an empty result (all zeros). */
    public static FetchResult empty() {
        return new FetchResult(0, 0, 0, 0);
    }

    /**
     * Combines two results by summing their counters.
     *
     * @param other the other result
     * @return a new combined result
     */
    public FetchResult combine(FetchResult other) {
        return new FetchResult(
                added + other.added,
                updated + other.updated,
                skipped + other.skipped,
                failed + other.failed);
    }

    public int added() { return added; }
    public int updated() { return updated; }
    public int skipped() { return skipped; }
    public int failed() { return failed; }

    @Override
    public String toString() {
        return "added=" + added + ", updated=" + updated
                + ", skipped=" + skipped + ", failed=" + failed;
    }
}
