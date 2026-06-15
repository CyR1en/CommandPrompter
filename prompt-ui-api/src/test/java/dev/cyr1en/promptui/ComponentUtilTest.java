package dev.cyr1en.promptui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

class ComponentUtilTest {

  @Test
  void miniMessageDeserializes() {
    var c = ComponentUtil.mini("<red>Hello</red>");
    assertNotNull(c);
  }

  @Test
  void miniMessageWithGradient() {
    var c = ComponentUtil.mini("<gradient:red:blue>Gradient</gradient>");
    assertNotNull(c);
  }

  @Test
  void stripColorRemovesCodes() {
    assertEquals("Hello", ComponentUtil.stripColor("§aHello"));
    assertEquals("Hello", ComponentUtil.stripColor("&aHello"));
  }

  @Test
  void stripColorWithMultipleCodes() {
    assertEquals("Hello World", ComponentUtil.stripColor("§aHello §bWorld"));
  }

  @Test
  void stripColorNoCodes() {
    assertEquals("Hello", ComponentUtil.stripColor("Hello"));
  }

  @Test
  void toLegacyConvertsHexCodes() {
    var result = ComponentUtil.toLegacy("&#FF0000Red");
    assertNotNull(result);
    assertTrue(result.contains("§"));
  }

  @Test
  void toLegacyNullReturnsNull() {
    assertNull(ComponentUtil.toLegacy(null));
  }

  @Test
  void toLegacyNoColor() {
    assertEquals("Hello", ComponentUtil.toLegacy("Hello"));
  }

  @Test
  void serializeIsInverseOfMini() {
    var c1 = ComponentUtil.mini("<green>Test</green>");
    var text = ComponentUtil.serialize(c1);
    var c2 = ComponentUtil.mini(text);
    assertEquals(c1, c2);
  }

  // ---- New tests for legacy code compatibility (Step 6 of PLANS.md) ----

  @Test
  void miniRendersLegacyColorCode() {
    var c = ComponentUtil.mini("&6Hello");
    assertNotNull(c);
    Style style = findStyleWithColor(c);
    assertNotNull(style, "expected a style with a color in the component tree");
    assertEquals(NamedTextColor.GOLD, style.color(), "&6 should map to GOLD");
  }

  @Test
  void miniRendersLegacyHexCode() {
    var c = ComponentUtil.mini("&#FF5555Hi");
    assertNotNull(c);
    Style style = findStyleWithColor(c);
    assertNotNull(style, "expected a style with a color in the component tree");
    assertNotNull(style.color(), "color should be set");
    assertEquals(0xFF5555, style.color().value(), "&#FF5555 should map to 0xFF5555");
  }

  @Test
  void miniRendersMiniMessage() {
    var c = ComponentUtil.mini("<red>Hi</red>");
    assertNotNull(c);
    Style style = findStyleWithColor(c);
    assertNotNull(style, "expected a style with a color in the component tree");
    assertEquals(NamedTextColor.RED, style.color());
  }

  @Test
  void miniRendersMixedFormat() {
    var c = ComponentUtil.mini("&6<bold>Hi</bold>");
    assertNotNull(c);
    Style style = findStyleWithColor(c);
    assertNotNull(style, "expected a style with a color in the component tree");
    assertEquals(NamedTextColor.GOLD, style.color());
    assertTrue(hasDecorationAnywhere(c, TextDecoration.BOLD));
  }

  @Test
  void miniHandlesNull() {
    assertNull(ComponentUtil.mini(null));
  }

  @Test
  void miniHandlesEmptyString() {
    var c = ComponentUtil.mini("");
    assertNotNull(c);
    assertEquals("", ((net.kyori.adventure.text.TextComponent) c).content());
  }

  @Test
  void miniPreservesPlainText() {
    var c = ComponentUtil.mini("hello");
    assertNotNull(c);
    assertInstanceOf(net.kyori.adventure.text.TextComponent.class, c);
    assertEquals("hello", ((net.kyori.adventure.text.TextComponent) c).content());
  }

  @Test
  void miniHandlesBoldCode() {
    var c = ComponentUtil.mini("&lBold");
    assertNotNull(c);
    assertTrue(hasDecorationAnywhere(c, TextDecoration.BOLD));
  }

  @Test
  void miniFallsBackOnMalformedMiniMessage() {
    // Unclosed tag should not throw — the helper must catch and return plain text.
    var c = ComponentUtil.mini("<red>no close");
    assertNotNull(c);
  }

  /**
   * Walks the Component tree and returns the first style that has a non-null color, or {@code null}
   * if none. Needed because MiniMessage may emit the color on a child component rather than the
   * root depending on the input shape.
   */
  private static Style findStyleWithColor(Component root) {
    if (root.style().color() != null) return root.style();
    for (Component child : root.children()) {
      Style found = findStyleWithColor(child);
      if (found != null) return found;
    }
    return null;
  }

  /**
   * Returns true if any component in the tree (root or any descendant) has the given decoration set
   * to ON. MiniMessage places decorations on a child component when they are applied as tags, so
   * the root alone is not always sufficient.
   */
  private static boolean hasDecorationAnywhere(Component root, TextDecoration decoration) {
    if (root.hasDecoration(decoration)) return true;
    for (Component child : root.children()) {
      if (hasDecorationAnywhere(child, decoration)) return true;
    }
    return false;
  }

  // --- miniToLegacy: MiniMessage string -> §X legacy string ---

  @Test
  void miniToLegacy_namedColor() {
    // <red>foo</red> in MiniMessage; §c in vanilla legacy is red.
    // The legacy serializer does NOT auto-append a reset (no §r) at the end
    // of the string, so just assert the colored text.
    assertEquals("§cfoo", ComponentUtil.miniToLegacy("<red>foo</red>"));
  }

  @Test
  void miniToLegacy_plainTextPassesThrough() {
    // No tags — MiniMessage deserializes to a plain text component, which the
    // legacy serializer should emit verbatim.
    assertEquals("hello", ComponentUtil.miniToLegacy("hello"));
  }

  @Test
  void miniToLegacy_nullAndEmpty() {
    assertNull(ComponentUtil.miniToLegacy(null), "null input must return null");
    assertEquals("", ComponentUtil.miniToLegacy(""), "empty input must return empty");
  }

  @Test
  void miniToLegacy_malformedFailsSoft() {
    // Adventure's MiniMessage parser is intentionally lenient — an unclosed
    // `<red>` does not throw; it just colors the rest of the string. To
    // exercise the catch block, use a tag with a backslash escape that the
    // convertLegacyInline helper can choke on. Use an unterminated escape:
    //   "Hello \\<red"
    // If the lenient parser also handles this, the test still documents that
    // miniToLegacy never throws NullPointerException to the caller.
    String input = "Hello \\<red";
    String out = ComponentUtil.miniToLegacy(input);
    assertNotNull(out, "miniToLegacy must never return null for non-null input");
    assertTrue(!out.isEmpty(), "miniToLegacy must never return empty for non-empty input");
  }

  @Test
  void miniToLegacy_nestedTagsFlatten() {
    // <bold><red>hi</red></bold> → bold + red on the same span.
    String result = ComponentUtil.miniToLegacy("<bold><red>hi</red></bold>");
    assertTrue(result.contains("hi"), "Result should still contain the text: " + result);
    assertTrue(result.contains("§c"), "Result should contain the red §c code: " + result);
    assertTrue(result.contains("§l"), "Result should contain the bold §l code: " + result);
  }

  @Test
  void miniToLegacy_doesNotProduceMiniTagsInOutput() {
    // The whole point of the conversion: ensure the output is consumable by
    // command paths (e.g. /say) that only understand legacy §X codes.
    String out = ComponentUtil.miniToLegacy("<red>hi</red>");
    assertTrue(!out.contains("<"), "Legacy output must not contain MiniMessage angle brackets: " + out);
  }
}
