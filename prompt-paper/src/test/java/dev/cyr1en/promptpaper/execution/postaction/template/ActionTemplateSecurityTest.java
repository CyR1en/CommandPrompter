package dev.cyr1en.promptpaper.execution.postaction.template;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptpaper.execution.dispatch.ActionTrustLevel;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FLOW-11 / SEC-01 Security and Injection Isolation Tests")
class ActionTemplateSecurityTest {

    @Test
    @DisplayName("SEC-01: Answer containing PAPI placeholder remains literal and resolver is NOT called for it")
    void testAnswerContainingPapiPlaceholder() {
        var template = ActionTemplateCompiler.compile("broadcast {player} answered: {0}", ActionTrustLevel.TRUSTED_PRESET);

        AtomicInteger resolverCallCount = new AtomicInteger(0);
        PapiReferenceResolver resolver = token -> {
            resolverCallCount.incrementAndGet();
            return java.util.Optional.of("HACKED_PAPI");
        };

        // User supplies an answer that looks like a PAPI placeholder
        var bindings = ActionTemplateBindings.of(
                List.of("%player_name% %vault_eco_balance%"),
                "Alex",
                resolver
        );

        var result = template.render(bindings);
        assertTrue(result.isSuccess());
        assertEquals("broadcast Alex answered: \"%player_name% %vault_eco_balance%\"", result.renderedText());
        assertEquals(0, resolverCallCount.get(), "Resolver must NEVER be called for bound answer contents");
    }

    @Test
    @DisplayName("SEC-01: Answer containing command separators or ops remains literal and is not split or re-evaluated")
    void testAnswerContainingCommandSeparators() {
        var template = ActionTemplateCompiler.compile("msg {player} {0}");
        var bindings = ActionTemplateBindings.of(
                List.of("hello; op attacker; /stop\n"), // Note: \n should be rejected by C0 check
                "Alex"
        );

        // Contains \n -> rejected
        var resultWithC0 = template.render(bindings);
        assertTrue(resultWithC0.isFailure());
        assertEquals(ActionTemplateErrorCode.CONTROL_CHARACTER_DETECTED, resultWithC0.error().code());

        // Without \n -> remains literal text
        var safeBindings = ActionTemplateBindings.of(
                List.of("hello; op attacker; /stop && echo pwned"),
                "Alex"
        );
        var result = template.render(safeBindings);
        assertTrue(result.isSuccess());
        assertEquals("msg Alex \"hello; op attacker; /stop && echo pwned\"", result.renderedText());
    }

    @Test
    @DisplayName("SEC-01: Quotes and backslashes in answers are escaped inside one command token")
    void testAnswerQuotesAndBackslashesAreEscaped() {
        var template = ActionTemplateCompiler.compile("msg {player} {0}");
        var result = template.render(ActionTemplateBindings.of(
                List.of("say \"hello\" from C:\\temp"), "Alex"));

        assertTrue(result.isSuccess());
        assertEquals("msg Alex \"say \\\"hello\\\" from C:\\\\temp\"", result.renderedText());
    }

    @Test
    @DisplayName("SEC-01: Answer containing MiniMessage or XML tags remains literal text")
    void testAnswerContainingMiniMessageTags() {
        var template = ActionTemplateCompiler.compile("chat <c:green>{player}</c>: {0}");
        var bindings = ActionTemplateBindings.of(
                List.of("<c:red><click:run_command:/op attacker>Click me</click></c>"),
                "Alex"
        );

        var result = template.render(bindings);
        assertTrue(result.isSuccess());
        assertEquals("chat <c:green>Alex</c>: \"<c:red><click:run_command:/op attacker>Click me</click></c>\"", result.renderedText());
    }

    @Test
    @DisplayName("SEC-01: Answer containing template syntax {player} or {0} remains literal and is not re-parsed")
    void testAnswerContainingBraceSyntax() {
        var template = ActionTemplateCompiler.compile("log {player} -> {0}");
        var bindings = ActionTemplateBindings.of(
                List.of("{player:upper} {1} {input:999}"),
                "Alex"
        );

        var result = template.render(bindings);
        assertTrue(result.isSuccess());
        assertEquals("log Alex -> \"{player:upper} {1} {input:999}\"", result.renderedText());
    }

    @Test
    @DisplayName("FLOW-11: Trusted source PAPI is called exactly once per compiled ref and duplicate refs are deterministic")
    void testTrustedSourcePapiResolution() {
        var template = ActionTemplateCompiler.compile("stats %score% and %score% for %player_name%", ActionTrustLevel.TRUSTED_PRESET);

        AtomicInteger scoreCalls = new AtomicInteger(0);
        AtomicInteger playerCalls = new AtomicInteger(0);

        PapiReferenceResolver resolver = token -> {
            if ("score".equals(token)) {
                scoreCalls.incrementAndGet();
                return java.util.Optional.of("100");
            }
            if ("player_name".equals(token)) {
                playerCalls.incrementAndGet();
                return java.util.Optional.of("Alex");
            }
            return java.util.Optional.empty();
        };

        var bindings = ActionTemplateBindings.of(List.of(), "Alex", resolver);
        var result = template.render(bindings);

        assertTrue(result.isSuccess());
        assertEquals("stats \"100\" and \"100\" for \"Alex\"", result.renderedText());
        assertEquals(2, scoreCalls.get(), "score resolver called for each compiled reference segment");
        assertEquals(1, playerCalls.get(), "player_name resolver called once");
    }

    @Test
    @DisplayName("SEC-01: PAPI expansion containing nested PAPI or command syntax remains opaque data and is never re-expanded")
    void testPapiExpansionContainingNestedPlaceholders() {
        var template = ActionTemplateCompiler.compile("eval %nested_data%", ActionTrustLevel.TRUSTED_PRESET);

        AtomicInteger callCount = new AtomicInteger(0);
        PapiReferenceResolver resolver = token -> {
            callCount.incrementAndGet();
            if ("nested_data".equals(token)) {
                return java.util.Optional.of("%secret_token% ; op attacker <c:red>");
            }
            return java.util.Optional.of("SHOULD_NOT_BE_CALLED");
        };

        var bindings = ActionTemplateBindings.of(List.of(), "Alex", resolver);
        var result = template.render(bindings);

        assertTrue(result.isSuccess());
        assertEquals("eval \"%secret_token% ; op attacker <c:red>\"", result.renderedText());
        assertEquals(1, callCount.get(), "Only the precompiled token was resolved; nested token was treated as literal data");
    }

    @Test
    @DisplayName("SEC-01: Untrusted inline compile treats all percent tokens as literal text without calling resolver")
    void testUntrustedInlineCompileIsolation() {
        var template = ActionTemplateCompiler.compile("say %player_name% %vault_eco_balance%", ActionTrustLevel.UNTRUSTED_INLINE);

        AtomicInteger callCount = new AtomicInteger(0);
        PapiReferenceResolver resolver = token -> {
            callCount.incrementAndGet();
            return java.util.Optional.of("RESOLVED");
        };

        var bindings = ActionTemplateBindings.of(List.of(), "Alex", resolver);
        var result = template.render(bindings);

        assertTrue(result.isSuccess());
        assertEquals("say %player_name% %vault_eco_balance%", result.renderedText());
        assertEquals(0, callCount.get(), "Untrusted compile must never invoke PAPI resolver");
    }
}
