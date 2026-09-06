package dev.cyr1en.promptpaper.execution.postaction.template;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptpaper.execution.dispatch.ActionTrustLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ActionTemplateCompiler Unit Tests")
class ActionTemplateCompilerTest {

  @Test
  @DisplayName("Compiles simple literal text")
  void testCompileLiteral() {
    var template = ActionTemplateCompiler.compile("say hello world");
    assertEquals("say hello world", template.source());
    assertEquals(ActionTrustLevel.UNTRUSTED_INLINE, template.trustLevel());
    assertEquals(1, template.segments().size());
    assertInstanceOf(ActionTemplateSegment.Literal.class, template.segments().get(0));
    assertEquals(
        "say hello world", ((ActionTemplateSegment.Literal) template.segments().get(0)).text());
  }

  @Test
  @DisplayName("Compiles legacy and core placeholders")
  void testCompilePlaceholders() {
    var template = ActionTemplateCompiler.compile("ban {player} {input:1} {input:2} {0} {input}");
    assertEquals(10, template.segments().size());
    assertTrue(template.referencedKeys().contains("player"));
    assertTrue(template.referencedKeys().contains("0"));
    assertTrue(template.referencedKeys().contains("1"));
  }

  @Test
  @DisplayName("Compiles placeholders with transformers")
  void testCompilePlaceholdersWithTransformers() {
    var template =
        ActionTemplateCompiler.compile(
            "msg {player:upper} {0:lower} {input:1:capitalize} {input:math(+5)}");
    assertEquals(8, template.segments().size());
    assertTrue(template.referencedKeys().contains("player"));
    assertTrue(template.referencedKeys().contains("0"));
  }

  @Test
  @DisplayName("Escapes braces, slashes, and percent signs properly")
  void testEscapes() {
    var template = ActionTemplateCompiler.compile("test \\{escaped\\} \\\\ \\%literal\\%");
    assertEquals(1, template.segments().size());
    assertEquals(
        "test {escaped} \\ %literal%",
        ((ActionTemplateSegment.Literal) template.segments().get(0)).text());
  }

  @Test
  @DisplayName("Rejects source longer than 1024 characters")
  void testSourceTooLong() {
    String longSource = "a".repeat(ActionTemplateLimits.MAX_SOURCE_LENGTH + 1);
    var ex =
        assertThrows(
            ActionTemplateException.class, () -> ActionTemplateCompiler.compile(longSource));
    assertEquals(ActionTemplateErrorCode.SOURCE_TOO_LONG, ex.errorCode());
  }

  @Test
  @DisplayName("Rejects raw C0 control characters in source")
  void testControlCharacters() {
    var ex1 =
        assertThrows(
            ActionTemplateException.class, () -> ActionTemplateCompiler.compile("say \u0000 bad"));
    assertEquals(ActionTemplateErrorCode.CONTROL_CHARACTER_DETECTED, ex1.errorCode());

    var ex2 =
        assertThrows(
            ActionTemplateException.class, () -> ActionTemplateCompiler.compile("say \n newline"));
    assertEquals(ActionTemplateErrorCode.CONTROL_CHARACTER_DETECTED, ex2.errorCode());

    var ex3 =
        assertThrows(
            ActionTemplateException.class, () -> ActionTemplateCompiler.compile("say \u007F del"));
    assertEquals(ActionTemplateErrorCode.CONTROL_CHARACTER_DETECTED, ex3.errorCode());
  }

  @Test
  @DisplayName("Rejects unclosed placeholder")
  void testUnclosedPlaceholder() {
    var ex =
        assertThrows(
            ActionTemplateException.class, () -> ActionTemplateCompiler.compile("say {player"));
    assertEquals(ActionTemplateErrorCode.MALFORMED_TEMPLATE, ex.errorCode());
  }

  @Test
  @DisplayName("Rejects unescaped closing brace")
  void testUnescapedClosingBrace() {
    var ex =
        assertThrows(
            ActionTemplateException.class, () -> ActionTemplateCompiler.compile("say hello}world"));
    assertEquals(ActionTemplateErrorCode.MALFORMED_TEMPLATE, ex.errorCode());
  }

  @Test
  @DisplayName("Rejects empty placeholder key")
  void testEmptyPlaceholderKey() {
    var ex =
        assertThrows(ActionTemplateException.class, () -> ActionTemplateCompiler.compile("say {}"));
    assertEquals(ActionTemplateErrorCode.MALFORMED_PLACEHOLDER, ex.errorCode());
  }

  @Test
  @DisplayName("Rejects zero-indexed input placeholder {input:0}")
  void testZeroIndexedInputPlaceholder() {
    var ex =
        assertThrows(
            ActionTemplateException.class, () -> ActionTemplateCompiler.compile("say {input:0}"));
    assertEquals(ActionTemplateErrorCode.MALFORMED_PLACEHOLDER, ex.errorCode());
  }

  @Test
  @DisplayName("Rejects chained transformers")
  void testChainedTransformers() {
    var ex =
        assertThrows(
            ActionTemplateException.class,
            () -> ActionTemplateCompiler.compile("say {player:upper:lower}"));
    assertEquals(ActionTemplateErrorCode.CHAINED_TRANSFORMER, ex.errorCode());
  }

  @Test
  @DisplayName("Rejects unknown transformer")
  void testUnknownTransformer() {
    var ex =
        assertThrows(
            ActionTemplateException.class,
            () -> ActionTemplateCompiler.compile("say {player:invalidTransform}"));
    assertEquals(ActionTemplateErrorCode.UNKNOWN_TRANSFORMER, ex.errorCode());
  }

  @Test
  @DisplayName("Trusted mode compiles valid PAPI tokens into Papi segments")
  void testTrustedPapiCompilation() {
    var template =
        ActionTemplateCompiler.compile(
            "say %player_name% has %vault_eco_balance%", ActionTrustLevel.TRUSTED_PRESET);
    assertEquals(ActionTrustLevel.TRUSTED_PRESET, template.trustLevel());
    assertEquals(4, template.segments().size());

    assertInstanceOf(ActionTemplateSegment.Literal.class, template.segments().get(0));
    assertInstanceOf(ActionTemplateSegment.Papi.class, template.segments().get(1));
    assertEquals("player_name", ((ActionTemplateSegment.Papi) template.segments().get(1)).token());

    assertInstanceOf(ActionTemplateSegment.Literal.class, template.segments().get(2));
    assertInstanceOf(ActionTemplateSegment.Papi.class, template.segments().get(3));
    assertEquals(
        "vault_eco_balance", ((ActionTemplateSegment.Papi) template.segments().get(3)).token());

    assertTrue(template.papiTokens().contains("player_name"));
    assertTrue(template.papiTokens().contains("vault_eco_balance"));
  }

  @Test
  @DisplayName("Untrusted mode does not compile PAPI tokens and treats them as literal text")
  void testUntrustedPapiCompilation() {
    var template =
        ActionTemplateCompiler.compile(
            "say %player_name% has %vault_eco_balance%", ActionTrustLevel.UNTRUSTED_INLINE);
    assertEquals(ActionTrustLevel.UNTRUSTED_INLINE, template.trustLevel());
    assertEquals(1, template.segments().size());
    assertInstanceOf(ActionTemplateSegment.Literal.class, template.segments().get(0));
    assertEquals(
        "say %player_name% has %vault_eco_balance%",
        ((ActionTemplateSegment.Literal) template.segments().get(0)).text());
    assertTrue(template.papiTokens().isEmpty());
  }

  @Test
  @DisplayName("Raw percent and invalid PAPI tokens in trusted mode remain literal text")
  void testRawPercentInTrustedMode() {
    var template =
        ActionTemplateCompiler.compile(
            "give 100% bonus % %invalid token% %% %", ActionTrustLevel.TRUSTED_PRESET);
    assertEquals(1, template.segments().size());
    assertInstanceOf(ActionTemplateSegment.Literal.class, template.segments().get(0));
    assertEquals(
        "give 100% bonus % %invalid token% %% %",
        ((ActionTemplateSegment.Literal) template.segments().get(0)).text());
    assertTrue(template.papiTokens().isEmpty());
  }

  @Test
  @DisplayName("Rejects templates with more than 128 segments")
  void testSegmentLimit() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 65; i++) {
      sb.append("a{player}");
    }
    // 65 * 2 = 130 segments (> 128)
    var ex =
        assertThrows(
            ActionTemplateException.class, () -> ActionTemplateCompiler.compile(sb.toString()));
    assertEquals(ActionTemplateErrorCode.TOO_MANY_SEGMENTS, ex.errorCode());
  }

  @Test
  @DisplayName("Rejects templates with more than 128 references")
  void testReferenceLimit() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 129; i++) {
      sb.append("{0}");
    }
    var ex =
        assertThrows(
            ActionTemplateException.class, () -> ActionTemplateCompiler.compile(sb.toString()));
    assertEquals(ActionTemplateErrorCode.TOO_MANY_REFERENCES, ex.errorCode());
  }
}
