package com.alexbatalov.fallout2ce;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

// Edits only requested values, retaining unknown keys, comments and line endings.
final class IniDocument {
    static final class Entry {
        final String section, key, value, description;
        Entry(String section, String key, String value, String description) {
            this.section = section;
            this.key = key;
            this.value = value;
            this.description = description;
        }
        String id() { return section.toLowerCase(Locale.ROOT) + "\0" + key.toLowerCase(Locale.ROOT); }
    }

    private final List<String> lines;
    private final String newline;
    private final boolean bom;

    IniDocument(String text) {
        bom = text.startsWith("\uFEFF");
        if (bom) text = text.substring(1);
        newline = text.contains("\r\n") ? "\r\n" : "\n";
        lines = new ArrayList<>(Arrays.asList(text.split("\r?\n", -1)));
    }

    // The engine gives semicolon comments precedence over hash comments.
    private static int commentAt(String line) {
        int index = line.indexOf(';');
        return index >= 0 ? index : line.indexOf('#');
    }

    private static String content(String line) {
        int comment = commentAt(line);
        return (comment < 0 ? line : line.substring(0, comment)).trim();
    }

    private static String section(String line) {
        String value = content(line);
        int end = value.indexOf(']');
        return value.startsWith("[") && end > 0 ? value.substring(1, end).trim() : null;
    }

    private static Entry entry(String section, String line, String description) {
        String value = content(line);
        int equals = value.indexOf('=');
        if (value.startsWith("[") || equals <= 0) return null;
        return new Entry(section, value.substring(0, equals).trim(),
                value.substring(equals + 1).trim(), description);
    }

    List<Entry> entries() {
        LinkedHashMap<String, Entry> result = new LinkedHashMap<>();
        String current = "";
        StringBuilder comments = new StringBuilder();
        for (String line : lines) {
            String heading = section(line);
            if (heading != null) {
                current = heading;
                comments.setLength(0);
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.startsWith(";") || trimmed.startsWith("#")) {
                if (comments.length() > 0) comments.append('\n');
                comments.append(trimmed.substring(1).trim());
                continue;
            }
            Entry item = entry(current, line, comments.toString());
            if (item != null) result.put(item.id(), item);
            comments.setLength(0);
        }
        return new ArrayList<>(result.values());
    }

    String get(String section, String key, String fallback) {
        for (Entry entry : entries()) {
            if (entry.section.equalsIgnoreCase(section) && entry.key.equalsIgnoreCase(key)) return entry.value;
        }
        return fallback;
    }

    void set(String wantedSection, String key, String value) {
        if (wantedSection.contains("\n") || wantedSection.contains("\r") || wantedSection.contains("]")
                || key.isEmpty() || key.contains("=") || key.contains("\n") || key.contains("\r")
                || value.contains("\n") || value.contains("\r") || value.contains(";") || value.contains("#")) {
            throw new IllegalArgumentException("The value must fit on one line and cannot contain ; or #.");
        }
        String current = "";
        int insertion = -1;
        boolean found = false;
        for (int i = 0; i < lines.size(); i++) {
            String heading = section(lines.get(i));
            if (heading != null) current = heading;
            if (!current.equalsIgnoreCase(wantedSection)) continue;
            insertion = i + 1;
            Entry old = heading == null ? entry(current, lines.get(i), "") : null;
            if (old == null || !old.key.equalsIgnoreCase(key)) continue;
            found = true;
            if (old.value.equals(value)) continue;
            String line = lines.get(i);
            int equals = line.indexOf('=');
            int start = equals + 1;
            while (start < line.length() && Character.isWhitespace(line.charAt(start))) start++;
            int comment = commentAt(line);
            int end = comment < 0 ? line.length() : comment;
            while (end > start && Character.isWhitespace(line.charAt(end - 1))) end--;
            lines.set(i, line.substring(0, start) + value + line.substring(end));
        }
        if (found) return;
        if (insertion < 0) {
            if (!lines.isEmpty() && !lines.get(lines.size() - 1).isEmpty()) lines.add("");
            if (!wantedSection.isEmpty()) lines.add("[" + wantedSection + "]");
            lines.add(key + "=" + value);
        } else {
            lines.add(insertion, key + "=" + value);
        }
        if (!lines.get(lines.size() - 1).isEmpty()) lines.add("");
    }

    @Override public String toString() {
        StringBuilder result = new StringBuilder(bom ? "\uFEFF" : "");
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) result.append(newline);
            result.append(lines.get(i));
        }
        return result.toString();
    }
}
