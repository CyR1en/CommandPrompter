package dev.cyr1en.promptpaper.validation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.hook.HookContainer;
import dev.cyr1en.promptpaper.hook.hooks.PapiHook;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JSExprValidatorTest extends MockBukkitTest {

  @Test
  void trueExpressionPasses() {
    var v = new JSExprValidator("test", "true", "fail", null, plugin);
    assertTrue(v.validate("anything"));
  }

  @Test
  void falseExpressionFails() {
    var v = new JSExprValidator("test", "false", "fail", null, plugin);
    assertFalse(v.validate("anything"));
  }

  @Test
  void promptInputIsReplaced() {
    var v = new JSExprValidator("test", "'%prompt_input%' == 'hello'", "fail", null, plugin);
    assertTrue(v.validate("hello"));
    assertFalse(v.validate("world"));
  }

  @Test
  void inputIsDataInQuotedAndBarePlaceholders() {
    var quoted =
        new JSExprValidator("quoted", "'%prompt_input%' == 'allowed'", "fail", null, plugin);
    assertFalse(quoted.validate("' == '' || true || '"));
    assertTrue(quoted.validate("allowed"));
    var bare = new JSExprValidator("bare", "%prompt_input% + 1 === 3", "fail", null, plugin);
    assertTrue(bare.validate("2"));
    assertFalse(bare.validate("(function() { throw new Error('injected'); })()"));
    assertFalse(
        new JSExprValidator("bare", "%prompt_input%", "fail", null, plugin).validate("true"));
  }

  @Test
  void embeddedQuotedInputPreservesQuotesAndBackslashes() {
    var validator =
        new JSExprValidator(
            "embedded",
            "'before %prompt_input% after' === 'before ' + '%prompt_input%' + ' after'",
            "fail",
            null,
            plugin);
    assertTrue(validator.validate("a'\\b\"c"));
    assertTrue(validator.validate("%player_name%"));
  }

  @Test
  void quoteBreakoutCannotExecuteServerJavaScript() {
    var marker = "commandprompter.validation.injection";
    var validator =
        new JSExprValidator("injection", "'%prompt_input%' === 'allowed'", "fail", null, plugin);
    try {
      System.clearProperty(marker);
      var result =
          validator.validate(
              "'; Java.type('java.lang.System').setProperty('"
                  + marker
                  + "', 'executed'); true; //");
      assertAll(() -> assertFalse(result), () -> assertNull(System.getProperty(marker)));
    } finally {
      System.clearProperty(marker);
    }
  }

  @Test
  void papiExpandsConfigurationWithoutReceivingPlayerInput() {
    var player = createPlayer();
    var hooks = mock(HookContainer.class);
    var papi = mock(PapiHook.class);
    when(plugin.getHookContainer()).thenReturn(hooks);
    when(hooks.getHook(PapiHook.class)).thenReturn(Optional.of(papi));
    when(papi.setPlaceholder(eq(player), anyString()))
        .thenAnswer(
            invocation -> {
              String source = invocation.getArgument(1);
              assertFalse(source.contains("%attacker%"));
              assertFalse(source.contains("%prompt_input%"));
              return source.replace("%configured%", "yes");
            });
    var validator =
        new JSExprValidator(
            "papi",
            "'%prompt_input%'.length === 10 && '%configured%' === 'yes'",
            "fail",
            player,
            plugin);
    assertTrue(validator.validate("%attacker%"));
    verify(papi).setPlaceholder(eq(player), anyString());
  }

  @Test
  void nonBooleanExpressionFails() {
    var v = new JSExprValidator("test", "'just a string'", "fail", null, plugin);
    assertFalse(v.validate("x"));
  }

  @Test
  void blankExpressionReturnsFalse() {
    var v = new JSExprValidator("test", "   ", "fail", null, plugin);
    assertFalse(v.validate("x"));
  }

  @Test
  void aliasReturnsConfiguredValue() {
    var v = new JSExprValidator("myAlias", "true", "msg", null, plugin);
    assertEquals("myAlias", v.alias());
  }

  @Test
  void messageOnFailReturnsConfiguredValue() {
    var v = new JSExprValidator("x", "true", "Bad JS!", null, plugin);
    assertEquals("Bad JS!", v.messageOnFail());
  }
}
