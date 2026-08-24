package dev.cyr1en.promptcore.logic.transform;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class TemplateCompilerTest {

  @Test
  void compile_literalOnly() {
    CompiledTemplate template = TemplateCompiler.compile("give player diamond 5");

    assertEquals("give player diamond 5", template.source());
    assertEquals(1, template.segments().size());
    assertInstanceOf(LiteralSegment.class, template.segments().get(0));
    assertEquals("give player diamond 5", ((LiteralSegment) template.segments().get(0)).text());
    assertTrue(template.referencedKeys().isEmpty());
  }

  @Test
  void compile_simplePlaceholder() {
    CompiledTemplate template = TemplateCompiler.compile("give {0} diamond {1}");

    assertEquals(4, template.segments().size());
    assertInstanceOf(LiteralSegment.class, template.segments().get(0));
    assertInstanceOf(ReferenceSegment.class, template.segments().get(1));
    assertInstanceOf(LiteralSegment.class, template.segments().get(2));
    assertInstanceOf(ReferenceSegment.class, template.segments().get(3));

    ReferenceSegment ref0 = (ReferenceSegment) template.segments().get(1);
    assertEquals("0", ref0.key());
    assertInstanceOf(NoOpTransformer.class, ref0.transformer());

    ReferenceSegment ref1 = (ReferenceSegment) template.segments().get(3);
    assertEquals("1", ref1.key());
    assertInstanceOf(NoOpTransformer.class, ref1.transformer());

    assertEquals(List.of("0", "1"), List.copyOf(template.referencedKeys()));
  }

  @Test
  void compile_allTransformers() {
    CompiledTemplate tUpper = TemplateCompiler.compile("{0:upper}");
    assertInstanceOf(
        UpperTransformer.class, ((ReferenceSegment) tUpper.segments().get(0)).transformer());

    CompiledTemplate tLower = TemplateCompiler.compile("{0:lower}");
    assertInstanceOf(
        LowerTransformer.class, ((ReferenceSegment) tLower.segments().get(0)).transformer());

    CompiledTemplate tCap = TemplateCompiler.compile("{0:capitalize}");
    assertInstanceOf(
        CapitalizeTransformer.class, ((ReferenceSegment) tCap.segments().get(0)).transformer());

    CompiledTemplate tTrim = TemplateCompiler.compile("{0:trim}");
    assertInstanceOf(
        TrimTransformer.class, ((ReferenceSegment) tTrim.segments().get(0)).transformer());

    CompiledTemplate tStrip = TemplateCompiler.compile("{0:stripcolor}");
    assertInstanceOf(
        StripColorTransformer.class, ((ReferenceSegment) tStrip.segments().get(0)).transformer());

    CompiledTemplate tRound = TemplateCompiler.compile("{0:round}");
    assertInstanceOf(
        RoundTransformer.class, ((ReferenceSegment) tRound.segments().get(0)).transformer());

    CompiledTemplate tDef = TemplateCompiler.compile("{0:default=\"N/A\"}");
    ReferenceSegment refDef = (ReferenceSegment) tDef.segments().get(0);
    assertInstanceOf(DefaultTransformer.class, refDef.transformer());
    assertEquals("N/A", ((DefaultTransformer) refDef.transformer()).defaultValue());

    CompiledTemplate tMath = TemplateCompiler.compile("{0:math(*1.5 +10)}");
    ReferenceSegment refMath = (ReferenceSegment) tMath.segments().get(0);
    assertInstanceOf(MathTransformer.class, refMath.transformer());
  }

  @Test
  void compile_escapes() {
    CompiledTemplate template =
        TemplateCompiler.compile("Literal \\{not_tag\\} and \\\\ backslash");

    assertEquals(1, template.segments().size());
    LiteralSegment literal = (LiteralSegment) template.segments().get(0);
    assertEquals("Literal {not_tag} and \\ backslash", literal.text());
  }

  @Test
  void compile_defaultWithEscapedQuotes() {
    CompiledTemplate template =
        TemplateCompiler.compile("{0:default=\"quoted \\\"value\\\" and \\\\ backslash\"}");

    ReferenceSegment ref = (ReferenceSegment) template.segments().get(0);
    DefaultTransformer def = (DefaultTransformer) ref.transformer();
    assertEquals("quoted \"value\" and \\ backslash", def.defaultValue());
  }

  @Test
  void compile_rejectsMalformedEscapes() {
    TransformException ex1 =
        assertThrows(
            TransformException.class, () -> TemplateCompiler.compile("Invalid \\x escape"));
    assertEquals(TransformErrorCode.MALFORMED_ESCAPE, ex1.code());

    TransformException ex2 =
        assertThrows(
            TransformException.class, () -> TemplateCompiler.compile("Dangling escape \\"));
    assertEquals(TransformErrorCode.MALFORMED_ESCAPE, ex2.code());
  }

  @Test
  void compile_rejectsUnescapedClosingBrace() {
    TransformException ex =
        assertThrows(TransformException.class, () -> TemplateCompiler.compile("Unescaped } brace"));
    assertEquals(TransformErrorCode.MALFORMED_TEMPLATE, ex.code());
  }

  @Test
  void compile_rejectsUnclosedPlaceholder() {
    TransformException ex =
        assertThrows(TransformException.class, () -> TemplateCompiler.compile("give {0 diamond"));
    assertEquals(TransformErrorCode.MALFORMED_TEMPLATE, ex.code());
  }

  @Test
  void compile_rejectsNestedPlaceholder() {
    TransformException ex =
        assertThrows(
            TransformException.class, () -> TemplateCompiler.compile("give {{0}} diamond"));
    assertEquals(TransformErrorCode.MALFORMED_PLACEHOLDER, ex.code());
  }

  @Test
  void compile_rejectsChainedTransformers() {
    TransformException ex1 =
        assertThrows(TransformException.class, () -> TemplateCompiler.compile("{0:upper:lower}"));
    assertEquals(TransformErrorCode.CHAINED_TRANSFORMER, ex1.code());

    TransformException ex2 =
        assertThrows(
            TransformException.class, () -> TemplateCompiler.compile("{0:math(+5):round}"));
    assertEquals(TransformErrorCode.CHAINED_TRANSFORMER, ex2.code());
  }

  @Test
  void compile_rejectsUnknownTransformer() {
    TransformException ex =
        assertThrows(TransformException.class, () -> TemplateCompiler.compile("{0:rot13}"));
    assertEquals(TransformErrorCode.UNKNOWN_TRANSFORMER, ex.code());
  }

  @Test
  void compile_rejectsMalformedRoundArguments() {
    TransformException ex =
        assertThrows(TransformException.class, () -> TemplateCompiler.compile("{0:round(2)}"));
    assertEquals(TransformErrorCode.MALFORMED_TRANSFORMER_ARGUMENT, ex.code());
  }

  @Test
  void compile_rejectsTemplateTooLong() {
    String longTemplate = "a".repeat(1025);
    TransformException ex =
        assertThrows(TransformException.class, () -> TemplateCompiler.compile(longTemplate));
    assertEquals(TransformErrorCode.TEMPLATE_TOO_LONG, ex.code());
  }

  @Test
  void compile_rejectsControlCharacters() {
    TransformException ex =
        assertThrows(TransformException.class, () -> TemplateCompiler.compile("give\0{0} diamond"));
    assertEquals(TransformErrorCode.CONTROL_CHARACTER_DETECTED, ex.code());
  }

  @Test
  void compile_customSyntax_valid() {
    TemplateSyntax syntax = new TemplateSyntax("[[", "]]", "|", "\\");
    CompiledTemplate template =
        TemplateCompiler.compile(
            "give [[0|upper]] diamond [[1|default=\"default value\"]]", syntax);

    assertEquals(4, template.segments().size());
    assertInstanceOf(LiteralSegment.class, template.segments().get(0));
    assertEquals("give ", ((LiteralSegment) template.segments().get(0)).text());

    ReferenceSegment ref0 = (ReferenceSegment) template.segments().get(1);
    assertEquals("0", ref0.key());
    assertInstanceOf(UpperTransformer.class, ref0.transformer());

    assertInstanceOf(LiteralSegment.class, template.segments().get(2));
    assertEquals(" diamond ", ((LiteralSegment) template.segments().get(2)).text());

    ReferenceSegment ref1 = (ReferenceSegment) template.segments().get(3);
    assertEquals("1", ref1.key());
    assertInstanceOf(DefaultTransformer.class, ref1.transformer());
    assertEquals("default value", ((DefaultTransformer) ref1.transformer()).defaultValue());
  }

  @Test
  void compile_customSyntax_multiCharEscape() {
    TemplateSyntax syntax = new TemplateSyntax("{{", "}}", ":", "%%");
    CompiledTemplate template =
        TemplateCompiler.compile("Literal %%{{not_tag%%}} and %%%% escape and {{0:lower}}", syntax);

    assertEquals(2, template.segments().size());
    LiteralSegment literal = (LiteralSegment) template.segments().get(0);
    assertEquals("Literal {{not_tag}} and %% escape and ", literal.text());

    ReferenceSegment ref = (ReferenceSegment) template.segments().get(1);
    assertEquals("0", ref.key());
    assertInstanceOf(LowerTransformer.class, ref.transformer());
  }

  @Test
  void compile_customSyntax_quotesWithSeparators() {
    TemplateSyntax syntax = new TemplateSyntax("[[", "]]", "|", "\\");
    CompiledTemplate template =
        TemplateCompiler.compile("[[0|default=\"piped|value|with\\\"quotes\\\"\"]] ]", syntax);

    assertEquals(2, template.segments().size());
    ReferenceSegment ref = (ReferenceSegment) template.segments().get(0);
    assertEquals("0", ref.key());
    assertInstanceOf(DefaultTransformer.class, ref.transformer());
    assertEquals(
        "piped|value|with\"quotes\"", ((DefaultTransformer) ref.transformer()).defaultValue());
    assertEquals(" ]", ((LiteralSegment) template.segments().get(1)).text());
  }

  @Test
  void compile_customSyntax_malformed() {
    TemplateSyntax syntax = new TemplateSyntax("[[", "]]", "|", "\\");

    // Unclosed placeholder
    TransformException ex1 =
        assertThrows(
            TransformException.class, () -> TemplateCompiler.compile("give [[0|upper", syntax));
    assertEquals(TransformErrorCode.MALFORMED_TEMPLATE, ex1.code());

    // Nested placeholder
    TransformException ex2 =
        assertThrows(
            TransformException.class, () -> TemplateCompiler.compile("give [[ [[0]] ]]", syntax));
    assertEquals(TransformErrorCode.MALFORMED_PLACEHOLDER, ex2.code());

    // Unescaped closing delimiter
    TransformException ex3 =
        assertThrows(
            TransformException.class, () -> TemplateCompiler.compile("give ]] diamond", syntax));
    assertEquals(TransformErrorCode.MALFORMED_TEMPLATE, ex3.code());

    // Chained transformers
    TransformException ex4 =
        assertThrows(
            TransformException.class, () -> TemplateCompiler.compile("[[0|upper|lower]]", syntax));
    assertEquals(TransformErrorCode.CHAINED_TRANSFORMER, ex4.code());
  }

  @Test
  void templateSyntax_validation() {
    // Valid custom syntax
    assertDoesNotThrow(() -> new TemplateSyntax("[[", "]]", "|", "\\"));
    assertDoesNotThrow(() -> new TemplateSyntax("${", "}", "#", "!"));

    // Null or empty
    assertThrows(NullPointerException.class, () -> new TemplateSyntax(null, "}", ":", "\\"));
    assertThrows(IllegalArgumentException.class, () -> new TemplateSyntax("", "}", ":", "\\"));
    assertThrows(IllegalArgumentException.class, () -> new TemplateSyntax("{", "", ":", "\\"));
    assertThrows(IllegalArgumentException.class, () -> new TemplateSyntax("{", "}", "", "\\"));
    assertThrows(IllegalArgumentException.class, () -> new TemplateSyntax("{", "}", ":", ""));

    // Whitespace or control or quotes
    assertThrows(IllegalArgumentException.class, () -> new TemplateSyntax("{ ", "}", ":", "\\"));
    assertThrows(IllegalArgumentException.class, () -> new TemplateSyntax("{\t", "}", ":", "\\"));
    assertThrows(IllegalArgumentException.class, () -> new TemplateSyntax("{\0", "}", ":", "\\"));
    assertThrows(IllegalArgumentException.class, () -> new TemplateSyntax("{\"", "}", ":", "\\"));
    assertThrows(IllegalArgumentException.class, () -> new TemplateSyntax("{'", "}", ":", "\\"));

    // Exceeds max token length
    assertThrows(
        IllegalArgumentException.class, () -> new TemplateSyntax("a".repeat(17), "}", ":", "\\"));

    // Equal or prefix overlaps
    assertThrows(IllegalArgumentException.class, () -> new TemplateSyntax("{", "{", ":", "\\"));
    assertThrows(IllegalArgumentException.class, () -> new TemplateSyntax("{{", "{", ":", "\\"));
    assertThrows(IllegalArgumentException.class, () -> new TemplateSyntax("{", "{{", ":", "\\"));
    assertThrows(IllegalArgumentException.class, () -> new TemplateSyntax("{", "}", "::", ":"));
    assertThrows(IllegalArgumentException.class, () -> new TemplateSyntax("{", "}", ":", "::"));
  }
}
