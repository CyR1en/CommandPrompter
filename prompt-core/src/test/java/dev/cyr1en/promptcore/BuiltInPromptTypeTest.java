package dev.cyr1en.promptcore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BuiltInPromptTypeTest {

  @Test
  void resolvesEveryAliasCaseInsensitivelyFromOneRegistry() {
    for (var type : BuiltInPromptType.values()) {
      for (var alias : type.aliases()) {
        assertEquals(type, BuiltInPromptType.resolve(alias.toUpperCase()).orElseThrow());
        assertTrue(BuiltInPromptType.allAliases().contains(alias));
      }
    }
  }
}
