package dev.cyr1en.promptcore;

import java.util.function.Predicate;

/**
 * Decides whether a matched tag should be skipped (ignored) by the parser.
 *
 * <p>This is a {@link Predicate} on the raw tag content — the text between the opening and closing
 * delimiters, without the delimiters themselves. When the predicate returns {@code true} for a
 * given content string, the parser treats the tag as a non-prompt: it is not classified as a prompt
 * tag or a PCM, and is left intact in the template command.
 *
 * <p>The primary use case is ignoring MiniMessage syntax (e.g. {@code <red>}, {@code </red>},
 * {@code <gradient:gold:yellow>...</gradient>}) when the prompt delimiters are angle brackets.
 * Because {@code prompt-core} has no MiniMessage dependency, the concrete filter is supplied by a
 * higher module (e.g. {@code prompt-paper}) at construction time.
 *
 * <p>Implementations must be thread-safe (stateless predicates are naturally so).
 */
@FunctionalInterface
public interface TagFilter extends Predicate<String> {}
