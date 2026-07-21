package dev.cyr1en.promptpaper.screen.dialog;

import java.util.Locale;

/**
 * The kind of input field a {@code <d:...>} prompt renders, driven by
 * the filter slot of a {@code PromptTag}. Unknown or empty filters
 * resolve to {@link #TEXT}.
 */
public enum DialogInputKind {
    TEXT,
    NUMBER,
    CHOICE,
    TAB,
    TITLE,
    BODY;

    /**
     * Parses a raw filter string into a kind, stripping trailing bracket
     * constraints. Accepts {@code "num"}, {@code "choice"}, {@code "tab"}
     * (case-insensitive); unknown/empty inputs return {@link #TEXT}.
     */
    public static DialogInputKind parse(String filter) {
        if (filter == null) return TEXT;
        var base = filter;
        var bracket = base.indexOf('[');
        if (bracket >= 0) base = base.substring(0, bracket);
        base = base.trim().toLowerCase(Locale.ROOT);
        return switch (base) {
            case "num", "number", "numberrange" -> NUMBER;
            case "choice" -> CHOICE;
            case "tab" -> TAB;
            case "title" -> TITLE;
            case "body" -> BODY;
            // `text` is silently treated as TEXT — the user-facing default.
            // The user is allowed to write it explicitly, but it has no
            // distinct semantics. Unknown filters fall through to TEXT.
            case "text", "" -> TEXT;
            default -> TEXT;
        };
    }
}
