package com.orthodoxicons.util;

import net.md_5.bungee.api.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text helpers for legacy + hex color translation and word wrapping. Uses the
 * Bungee {@link ChatColor} shipped with Spigot/Paper so it works identically on
 * every supported server platform.
 */
public final class Text {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private Text() {
    }

    /**
     * Translates legacy {@code &} codes and {@code &#rrggbb} hex codes into
     * section-sign colored text.
     *
     * @param input raw text (may be {@code null})
     * @return colorized text ({@code ""} when input is null)
     */
    public static String color(String input) {
        if (input == null) {
            return "";
        }
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder(input.length());
        while (matcher.find()) {
            String hex = matcher.group(1);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(ChatColor.of("#" + hex).toString()));
        }
        matcher.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    /**
     * Word-wraps a string to a maximum line length, preserving whole words.
     *
     * @param input   the text to wrap
     * @param maxLen  maximum characters per line
     * @return a list of wrapped lines (empty when input is blank)
     */
    public static List<String> wrap(String input, int maxLen) {
        List<String> lines = new ArrayList<>();
        if (input == null || input.isBlank()) {
            return lines;
        }
        StringBuilder line = new StringBuilder();
        for (String word : input.trim().split("\\s+")) {
            if (line.length() == 0) {
                line.append(word);
            } else if (line.length() + 1 + word.length() <= maxLen) {
                line.append(' ').append(word);
            } else {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    /**
     * Strips all color codes from a string.
     *
     * @param input colored text
     * @return plain text
     */
    public static String strip(String input) {
        return ChatColor.stripColor(color(input));
    }
}
