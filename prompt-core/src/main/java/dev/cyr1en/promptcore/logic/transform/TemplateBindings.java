package dev.cyr1en.promptcore.logic.transform;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Resolves named and indexed reference keys to bound string values during template rendering. */
@FunctionalInterface
public interface TemplateBindings {

  /**
   * Resolves a reference key to its bound value.
   *
   * @param key the reference key (e.g. "0", "player")
   * @return the bound value, or null if unbound
   */
  String get(String key);

  static TemplateBindings of(Map<String, String> map) {
    Objects.requireNonNull(map, "map must not be null");
    return map::get;
  }

  static TemplateBindings fromList(List<String> list) {
    Objects.requireNonNull(list, "list must not be null");
    return key -> {
      try {
        int idx = Integer.parseInt(key);
        if (idx >= 0 && idx < list.size()) {
          return list.get(idx);
        }
      } catch (NumberFormatException ignored) {
      }
      return null;
    };
  }

  static TemplateBindings fromIndexed(String... answers) {
    return fromList(List.of(answers));
  }

  static TemplateBindings fromFunction(Function<String, String> function) {
    Objects.requireNonNull(function, "function must not be null");
    return function::apply;
  }

  static TemplateBindings combine(TemplateBindings primary, TemplateBindings fallback) {
    Objects.requireNonNull(primary, "primary must not be null");
    Objects.requireNonNull(fallback, "fallback must not be null");
    return key -> {
      String val = primary.get(key);
      return val != null ? val : fallback.get(key);
    };
  }
}
