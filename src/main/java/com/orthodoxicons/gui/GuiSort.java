package com.orthodoxicons.gui;

import com.orthodoxicons.model.Icon;

import java.util.Comparator;

/**
 * Sorting modes available in the icon browser. Each mode exposes a comparator
 * used to order the currently-displayed icon list.
 */
public enum GuiSort {

    /** Alphabetical by icon name. */
    NAME(Comparator.comparing(Icon::name, String.CASE_INSENSITIVE_ORDER)),
    /** Alphabetical by saint. */
    SAINT(Comparator.comparing(Icon::saint, String.CASE_INSENSITIVE_ORDER)),
    /** Alphabetical by feast. */
    FEAST(Comparator.comparing(Icon::feast, String.CASE_INSENSITIVE_ORDER)),
    /** Alphabetical by category. */
    CATEGORY(Comparator.comparing(Icon::category, String.CASE_INSENSITIVE_ORDER)),
    /** Newest first by date added. */
    DATE_ADDED(Comparator.comparing(Icon::dateAdded).reversed()),
    /** Alphabetical by provider id. */
    PROVIDER(Comparator.comparing(Icon::providerId, String.CASE_INSENSITIVE_ORDER));

    private final Comparator<Icon> comparator;

    GuiSort(Comparator<Icon> comparator) {
        this.comparator = comparator;
    }

    /** @return the comparator implementing this sort */
    public Comparator<Icon> comparator() {
        return comparator;
    }

    /**
     * @return the next sort mode in cycle order
     */
    public GuiSort next() {
        GuiSort[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    /**
     * Parses a sort mode leniently, defaulting to {@link #NAME}.
     *
     * @param raw raw name
     * @return the parsed sort
     */
    public static GuiSort parse(String raw) {
        if (raw == null) {
            return NAME;
        }
        try {
            return valueOf(raw.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return NAME;
        }
    }
}
