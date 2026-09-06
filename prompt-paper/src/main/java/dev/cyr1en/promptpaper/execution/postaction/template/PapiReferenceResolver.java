package dev.cyr1en.promptpaper.execution.postaction.template;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Functional interface for resolving precompiled PlaceholderAPI reference tokens.
 *
 * <p>Decoupled from Bukkit and PlaceholderAPI for testing and execution isolation.
 */
@FunctionalInterface
public interface PapiReferenceResolver {

  /**
   * Resolves a single PAPI token (e.g. "player_name") to its expansion string.
   *
   * @param token the PAPI placeholder token without percent delimiters
   * @return the optional resolved expansion string
   */
  Optional<String> resolve(String token);

  static PapiReferenceResolver empty() {
    return token -> Optional.empty();
  }

  static PapiReferenceResolver fromMap(Map<String, String> map) {
    Objects.requireNonNull(map, "map must not be null");
    return token -> Optional.ofNullable(map.get(token));
  }

  static PapiReferenceResolver fromFunction(Function<String, String> function) {
    Objects.requireNonNull(function, "function must not be null");
    return token -> Optional.ofNullable(function.apply(token));
  }
}
