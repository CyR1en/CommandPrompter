package dev.cyr1en.promptui.version;

import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks whether a {@link Version} falls within a semver-style range.
 *
 * <p>Supports operators: {@code >=}, {@code >}, {@code <=}, {@code <}, {@code =}.
 * Multiple conditions can be combined with spaces (e.g., {@code ">=1.21.0 <1.22.0"}).
 * Kept for future multi-version support.</p>
 */
public final class VersionMatcher {

    private static final Pattern CONDITION_PATTERN =
        Pattern.compile("(>=|>|<=|<|=)\\s*(\\d+(?:\\.\\d+)*(?:-pre\\d+)?)");

    private final String expression;

    /**
     * Creates a matcher from a range expression string.
     *
     * @param expression e.g. {@code ">=1.21.0 <1.22.0"}
     */
    public VersionMatcher(@NotNull String expression) {
        this.expression = expression;
    }

    /**
     * Tests whether the given version matches the range expression.
     *
     * @param version the version to test
     * @return true if the version satisfies all conditions
     * @throws IllegalArgumentException if the expression cannot be parsed
     */
    public boolean matches(@NotNull Version version) {
        Matcher matcher = CONDITION_PATTERN.matcher(expression);
        while (matcher.find()) {
            String operator = matcher.group(1);
            Version target = Version.of(matcher.group(2));
            if (!evaluate(operator, version, target)) {
                return false;
            }
        }
        return true;
    }

    /** Evaluates a single operator condition against two versions. */
    private static boolean evaluate(String operator, Version version, Version target) {
        int cmp = version.compareTo(target);
        return switch (operator) {
            case ">=" -> cmp >= 0;
            case ">"  -> cmp > 0;
            case "<=" -> cmp <= 0;
            case "<"  -> cmp < 0;
            case "="  -> cmp == 0;
            default -> throw new IllegalArgumentException("Unknown operator: " + operator);
        };
    }
}
