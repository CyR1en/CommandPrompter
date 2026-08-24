package dev.cyr1en.promptpaper.execution.postaction.template;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptcore.logic.transform.MathMode;
import dev.cyr1en.promptcore.logic.transform.TransformNotice;
import dev.cyr1en.promptpaper.execution.dispatch.ActionTrustLevel;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CompiledActionTemplate Rendering and Logic Unit Tests")
class CompiledActionTemplateTest {

    @Test
    @DisplayName("Renders legacy placeholders {player}, {input}, {input:1}, {input:2}, and {0}")
    void testRenderLegacyPlaceholders() {
        var template = ActionTemplateCompiler.compile("tell {player} {input} {input:1} {input:2} {0} {1}");
        var bindings = ActionTemplateBindings.of(List.of("first", "second"), "Alex");

        var result = template.render(bindings);
        assertTrue(result.isSuccess());
        assertEquals("tell Alex \"first\" \"first\" \"second\" \"first\" \"second\"", result.renderedText());
    }

    @Test
    @DisplayName("Renders transformers: upper, lower, capitalize, trim, stripcolor")
    void testTransformers() {
        var template = ActionTemplateCompiler.compile("echo {player:upper} {0:lower} {1:capitalize} {2:trim} {3:stripcolor}");
        var bindings = ActionTemplateBindings.of(List.of("UPPER_TO_LOWER", "steve", "   spaced   ", "&aColored"), "alex");

        var result = template.render(bindings);
        assertTrue(result.isSuccess());
        assertEquals("echo ALEX \"upper_to_lower\" \"Steve\" \"spaced\" \"Colored\"", result.renderedText());
    }

    @Test
    @DisplayName("Renders default value transformer when binding is missing")
    void testDefaultTransformer() {
        var template = ActionTemplateCompiler.compile("give {player} {0:default=\"diamond\"} {1:default=\"gold\"}");
        var bindings = ActionTemplateBindings.of(List.of("emerald"), "Alex");

        var result = template.render(bindings);
        assertTrue(result.isSuccess());
        assertEquals("give Alex \"emerald\" \"gold\"", result.renderedText());
    }

    @Test
    @DisplayName("Fails closed on missing binding without default transformer")
    void testMissingBindingFails() {
        var template = ActionTemplateCompiler.compile("give {player} {5}");
        var bindings = ActionTemplateBindings.of(List.of("emerald"), "Alex");

        var result = template.render(bindings);
        assertTrue(result.isFailure());
        assertEquals(ActionTemplateErrorCode.MISSING_BINDING, result.error().code());
    }

    @Test
    @DisplayName("Renders math transformer and records notices on legacy division by zero")
    void testMathTransformer() {
        var template = ActionTemplateCompiler.compile("points {0:math(+50 *2)} {input:1:math(/0)}");
        var bindings = ActionTemplateBindings.of(List.of("10", "100"), "Alex");

        var result = template.render(bindings, MathMode.LEGACY);
        assertTrue(result.isSuccess());
        assertEquals("points \"120.00\" \"0\"", result.renderedText());
        assertTrue(result.notices().contains(TransformNotice.DIVISION_BY_ZERO_SUBSTITUTED));
    }

    @Test
    @DisplayName("Math division by zero fails closed")
    void testMathDivisionByZero() {
        var template = ActionTemplateCompiler.compile("calc {0:math(/0)}");
        var bindings = ActionTemplateBindings.of(List.of("100"), "Alex");

        var result = template.render(bindings, MathMode.STRICT);
        assertTrue(result.isFailure());
        assertEquals(ActionTemplateErrorCode.DIVISION_BY_ZERO, result.error().code());
    }

    @Test
    @DisplayName("Math non numeric input fails closed")
    void testMathNonNumeric() {
        var template = ActionTemplateCompiler.compile("calc {0:math(+5)}");
        var bindings = ActionTemplateBindings.of(List.of("abc"), "Alex");

        var result = template.render(bindings);
        assertTrue(result.isFailure());
        assertEquals(ActionTemplateErrorCode.NON_NUMERIC_INPUT, result.error().code());
    }

    @Test
    @DisplayName("Renders trusted PAPI placeholders via resolver")
    void testTrustedPapiRendering() {
        var template = ActionTemplateCompiler.compile("say %player_name% balance: %vault_eco_balance%", ActionTrustLevel.TRUSTED_PRESET);
        var resolver = PapiReferenceResolver.fromMap(Map.of(
                "player_name", "Alex",
                "vault_eco_balance", "500.00"
        ));
        var bindings = ActionTemplateBindings.of(List.of(), "Alex", resolver);

        var result = template.render(bindings);
        assertTrue(result.isSuccess());
        assertEquals("say \"Alex\" balance: \"500.00\"", result.renderedText());
    }

    @Test
    @DisplayName("Fails closed when PAPI resolver is missing for PAPI reference")
    void testMissingPapiResolverFails() {
        var template = ActionTemplateCompiler.compile("say %player_name%", ActionTrustLevel.TRUSTED_PRESET);
        var bindings = ActionTemplateBindings.of(List.of(), "Alex", null);

        var result = template.render(bindings);
        assertTrue(result.isFailure());
        assertEquals(ActionTemplateErrorCode.PAPI_RESOLVER_MISSING, result.error().code());
    }

    @Test
    @DisplayName("Fails closed when PAPI resolver returns empty or throws")
    void testPapiResolutionFailure() {
        var template = ActionTemplateCompiler.compile("say %unknown_papi%", ActionTrustLevel.TRUSTED_PRESET);
        var resolver = PapiReferenceResolver.empty();
        var bindings = ActionTemplateBindings.of(List.of(), "Alex", resolver);

        var result = template.render(bindings);
        assertTrue(result.isFailure());
        assertEquals(ActionTemplateErrorCode.PAPI_RESOLUTION_FAILED, result.error().code());

        var throwingResolver = (PapiReferenceResolver) token -> {
            throw new RuntimeException("Resolver error");
        };
        var throwingBindings = ActionTemplateBindings.of(List.of(), "Alex", throwingResolver);
        var exResult = template.render(throwingBindings);
        assertTrue(exResult.isFailure());
        assertEquals(ActionTemplateErrorCode.PAPI_RESOLUTION_FAILED, exResult.error().code());
    }

    @Test
    @DisplayName("Fails closed when PAPI resolution exceeds max length (1024)")
    void testPapiValueTooLong() {
        var template = ActionTemplateCompiler.compile("say %long_val%", ActionTrustLevel.TRUSTED_PRESET);
        var resolver = PapiReferenceResolver.fromMap(Map.of(
                "long_val", "X".repeat(ActionTemplateLimits.MAX_INPUT_LENGTH + 1)
        ));
        var bindings = ActionTemplateBindings.of(List.of(), "Alex", resolver);

        var result = template.render(bindings);
        assertTrue(result.isFailure());
        assertEquals(ActionTemplateErrorCode.PAPI_VALUE_TOO_LONG, result.error().code());
    }

    @Test
    @DisplayName("Fails closed when PAPI resolution contains C0 control character")
    void testPapiControlCharacter() {
        var template = ActionTemplateCompiler.compile("say %bad_val%", ActionTrustLevel.TRUSTED_PRESET);
        var resolver = PapiReferenceResolver.fromMap(Map.of(
                "bad_val", "bad\u0000text"
        ));
        var bindings = ActionTemplateBindings.of(List.of(), "Alex", resolver);

        var result = template.render(bindings);
        assertTrue(result.isFailure());
        assertEquals(ActionTemplateErrorCode.PAPI_CONTROL_CHARACTER, result.error().code());
    }

    @Test
    @DisplayName("Fails closed when bound input exceeds max length (1024)")
    void testBoundInputTooLong() {
        var template = ActionTemplateCompiler.compile("say {0}");
        var bindings = ActionTemplateBindings.of(List.of("X".repeat(ActionTemplateLimits.MAX_INPUT_LENGTH + 1)), "Alex");

        var result = template.render(bindings);
        assertTrue(result.isFailure());
        assertEquals(ActionTemplateErrorCode.INPUT_TOO_LONG, result.error().code());
    }

    @Test
    @DisplayName("Fails closed when bound input contains C0 control characters")
    void testBoundInputControlChar() {
        var template = ActionTemplateCompiler.compile("say {0}");
        var bindings = ActionTemplateBindings.of(List.of("bad\u0007input"), "Alex");

        var result = template.render(bindings);
        assertTrue(result.isFailure());
        assertEquals(ActionTemplateErrorCode.CONTROL_CHARACTER_DETECTED, result.error().code());
    }

    @Test
    @DisplayName("Fails closed when rendered output exceeds limit (4096)")
    void testOutputTooLong() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            sb.append("{0}");
        }
        var template = ActionTemplateCompiler.compile(sb.toString());
        // 5 * 900 chars = 4500 chars (> 4096)
        var bindings = ActionTemplateBindings.of(List.of("A".repeat(900)), "Alex");

        var result = template.render(bindings);
        assertTrue(result.isFailure());
        assertEquals(ActionTemplateErrorCode.OUTPUT_TOO_LONG, result.error().code());
    }

    @Test
    @DisplayName("Respects stricter custom output length cap")
    void testCustomOutputLengthCap() {
        var template = ActionTemplateCompiler.compile("say {0}");
        var bindings = ActionTemplateBindings.of(List.of("12345678901234567890"), "Alex");

        var result = template.render(bindings, MathMode.LEGACY, 15);
        assertTrue(result.isFailure());
        assertEquals(ActionTemplateErrorCode.OUTPUT_TOO_LONG, result.error().code());
    }

    @Test
    @DisplayName("Locale determinism: produces identical output across different default locales")
    void testLocaleDeterminism() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            var template = ActionTemplateCompiler.compile("msg {player:upper} {0:lower} {1:math(+5.5)}");
            var bindings = ActionTemplateBindings.of(List.of("TITLE_INFO", "10"), "identity");

            var result = template.render(bindings);
            assertTrue(result.isSuccess());
            assertEquals("msg IDENTITY \"title_info\" \"15.50\"", result.renderedText());
        } finally {
            Locale.setDefault(original);
        }
    }
}
