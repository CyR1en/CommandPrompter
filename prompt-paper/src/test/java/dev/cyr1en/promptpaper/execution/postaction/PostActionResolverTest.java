package dev.cyr1en.promptpaper.execution.postaction;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptcore.DispatchTarget;
import dev.cyr1en.promptcore.PostCommandMeta;
import dev.cyr1en.promptcore.logic.condition.ConditionCompileOptions;
import dev.cyr1en.promptcore.logic.condition.ConditionCompiler;
import dev.cyr1en.promptpaper.execution.dispatch.ActionTrustLevel;
import dev.cyr1en.promptpaper.execution.dispatch.DispatchErrorKind;
import dev.cyr1en.promptpaper.execution.postaction.template.PapiReferenceResolver;
import dev.cyr1en.promptpaper.execution.runtime.DispatchContextSnapshot;
import dev.cyr1en.promptpaper.execution.runtime.InputCompletion;
import dev.cyr1en.promptpaper.preset.ConditionalPostCommandDefinition;
import dev.cyr1en.promptpaper.preset.ExecuteAs;
import dev.cyr1en.promptpaper.preset.ExecutionPolicy;
import dev.cyr1en.promptpaper.preset.PostCommand;
import dev.cyr1en.promptpaper.preset.PresetSnapshot;
import dev.cyr1en.promptpaper.preset.TrustedPresetAction;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PostActionResolver Tests")
class PostActionResolverTest {

    private final UUID initiator = UUID.randomUUID();

    private InputCompletion createCompletion(List<String> answers, PresetSnapshot snapshot) {
        return new InputCompletion(
                initiator,
                1L,
                0L,
                answers,
                "primary",
                null,
                snapshot,
                DispatchContextSnapshot.player()
        );
    }

    @Nested
    @DisplayName("Legacy Inline Resolution")
    class LegacyInlineTests {

        @Test
        @DisplayName("Resolves matching ON_COMPLETE legacy inline PCM")
        void resolvesMatchingOnComplete() {
            var pcm = new PostCommandMeta("say {0}", new int[]{0}, 10, false, DispatchTarget.CONSOLE, false);
            var completion = createCompletion(List.of("hello"), PresetSnapshot.empty());

            var resolved = PostActionResolver.resolve(pcm, ExecutionPolicy.ON_COMPLETE, completion, PapiReferenceResolver.empty());
            assertNotNull(resolved);
            assertFalse(resolved.isNoOp());
            assertEquals(ExecuteAs.CONSOLE, resolved.executeAs());
            assertEquals(10, resolved.delayTicks());
            assertEquals(ActionTrustLevel.UNTRUSTED_INLINE, resolved.template().trustLevel());
            assertNull(resolved.sourceId());
            assertNotNull(resolved.provenance());
            assertEquals(ActionTrustLevel.UNTRUSTED_INLINE, resolved.provenance().trustLevel());
            assertFalse(resolved.provenance().consoleDelegated());
        }

        @Test
        @DisplayName("Resolves matching ON_CANCEL legacy inline PCM")
        void resolvesMatchingOnCancel() {
            var pcm = new PostCommandMeta("say cancelled {0}", new int[]{0}, 0, true, DispatchTarget.PLAYER, false);
            var completion = createCompletion(List.of("reason"), PresetSnapshot.empty());

            var resolved = PostActionResolver.resolve(pcm, ExecutionPolicy.ON_CANCEL, completion, PapiReferenceResolver.empty());
            assertNotNull(resolved);
            assertFalse(resolved.isNoOp());
            assertEquals(ExecuteAs.PLAYER, resolved.executeAs());
            assertEquals(0, resolved.delayTicks());
        }

        @Test
        @DisplayName("Skips policy mismatched legacy inline PCM")
        void skipsMismatchedPolicy() {
            var onCancelPcm = new PostCommandMeta("say cancel", new int[]{}, 0, true, DispatchTarget.PLAYER, false);
            var onCompletePcm = new PostCommandMeta("say complete", new int[]{}, 0, false, DispatchTarget.PLAYER, false);
            var completion = createCompletion(List.of(), PresetSnapshot.empty());

            assertNull(PostActionResolver.resolve(onCancelPcm, ExecutionPolicy.ON_COMPLETE, completion, PapiReferenceResolver.empty()));
            assertNull(PostActionResolver.resolve(onCompletePcm, ExecutionPolicy.ON_CANCEL, completion, PapiReferenceResolver.empty()));
        }

        @Test
        @DisplayName("Inline template does not parse %papi% references in untrusted mode")
        void inlineDoesNotParsePapi() {
            var pcm = new PostCommandMeta("give {player} %vault_eco_balance%", new int[]{}, 0, false, DispatchTarget.PLAYER, false);
            var completion = createCompletion(List.of(), PresetSnapshot.empty());

            var resolved = PostActionResolver.resolve(pcm, ExecutionPolicy.ON_COMPLETE, completion, PapiReferenceResolver.empty());
            assertNotNull(resolved);
            assertTrue(resolved.template().papiTokens().isEmpty());
        }
    }

    @Nested
    @DisplayName("Preset PostCommand Resolution")
    class PresetPostCommandTests {

        @Test
        @DisplayName("Resolves legacy PostCommand from snapshot with authoritative policy")
        void resolvesLegacyPresetAuthoritativePolicy() {
            var postCmd = new PostCommand("reward_cmd", "give {player} diamond {input:1}", ExecutionPolicy.ON_COMPLETE, ExecuteAs.CONSOLE, 15);
            var snapshot = new PresetSnapshot(Map.of(), Map.of("reward_cmd", postCmd), Map.of(), Map.of(), 1L);
            var completion = createCompletion(List.of("64"), snapshot);

            // Parser hint has onCancel = true, but preset has ON_COMPLETE -> preset wins!
            var pcm = new PostCommandMeta("reward_cmd", new int[]{}, 0, true, DispatchTarget.PLAYER, true);

            var resolved = PostActionResolver.resolve(pcm, ExecutionPolicy.ON_COMPLETE, completion, PapiReferenceResolver.empty());
            assertNotNull(resolved);
            assertEquals(ExecuteAs.CONSOLE, resolved.executeAs());
            assertEquals(15, resolved.delayTicks());
            assertEquals("reward_cmd", resolved.sourceId());
            assertEquals(ActionTrustLevel.TRUSTED_PRESET, resolved.template().trustLevel());
            assertNotNull(resolved.provenance());
            assertEquals(ActionTrustLevel.TRUSTED_PRESET, resolved.provenance().trustLevel());
            assertTrue(resolved.provenance().consoleDelegated(), "CONSOLE executeAs in trusted preset must set consoleDelegated = true");
            assertEquals("reward_cmd", resolved.provenance().sourceId());

            // On cancel run -> skipped because preset policy is ON_COMPLETE
            assertNull(PostActionResolver.resolve(pcm, ExecutionPolicy.ON_CANCEL, completion, PapiReferenceResolver.empty()));
        }

        @Test
        @DisplayName("Resolves legacy PostCommand with PLAYER executeAs and consoleDelegated = false")
        void resolvesLegacyPresetPlayerExecuteAs() {
            var postCmd = new PostCommand("player_cmd", "give {player} diamond", ExecutionPolicy.ON_COMPLETE, ExecuteAs.PLAYER, 0);
            var snapshot = new PresetSnapshot(Map.of(), Map.of("player_cmd", postCmd), Map.of(), Map.of(), 1L);
            var completion = createCompletion(List.of(), snapshot);
            var pcm = new PostCommandMeta("player_cmd", new int[]{}, 0, false, DispatchTarget.PLAYER, true);

            var resolved = PostActionResolver.resolve(pcm, ExecutionPolicy.ON_COMPLETE, completion, PapiReferenceResolver.empty());
            assertNotNull(resolved);
            assertEquals(ExecuteAs.PLAYER, resolved.executeAs());
            assertEquals("player_cmd", resolved.sourceId());
            assertEquals(ActionTrustLevel.TRUSTED_PRESET, resolved.template().trustLevel());
            assertNotNull(resolved.provenance());
            assertEquals(ActionTrustLevel.TRUSTED_PRESET, resolved.provenance().trustLevel());
            assertFalse(resolved.provenance().consoleDelegated());
        }

        @Test
        @DisplayName("Unknown preset ID fails closed with typed error")
        void unknownPresetIdFailsClosed() {
            var snapshot = PresetSnapshot.empty();
            var completion = createCompletion(List.of(), snapshot);
            var pcm = new PostCommandMeta("non_existent_preset", new int[]{}, 0, false, DispatchTarget.PLAYER, true);

            var ex = assertThrows(
                    PostActionResolutionException.class,
                    () -> PostActionResolver.resolve(pcm, ExecutionPolicy.ON_COMPLETE, completion, PapiReferenceResolver.empty())
            );
            assertEquals(DispatchErrorKind.INVALID_REQUEST, ex.getError().kind());
            assertTrue(ex.getError().detail().contains("non_existent_preset"));
        }
    }

    @Nested
    @DisplayName("Conditional PostCommand Resolution")
    class ConditionalPostCommandTests {

        @Test
        @DisplayName("Selects ifTrue branch when condition evaluates to true")
        void selectsIfTrueBranch() {
            var cond = ConditionCompiler.compile("{0} equals \"yes\"", ConditionCompileOptions.forPreset());
            var trueAction = TrustedPresetAction.of("say accepted {player}", ExecuteAs.PLAYER, 5);
            var falseAction = TrustedPresetAction.of("say rejected {player}", ExecuteAs.CONSOLE, 10);
            var def = new ConditionalPostCommandDefinition("cond1", cond, ExecutionPolicy.ON_COMPLETE, trueAction, falseAction);

            var snapshot = new PresetSnapshot(Map.of(), Map.of(), Map.of(), Map.of("cond1", def), 1L);
            var completion = createCompletion(List.of("yes"), snapshot);
            var pcm = new PostCommandMeta("cond1", new int[]{}, 0, false, DispatchTarget.PLAYER, true);

            var resolved = PostActionResolver.resolve(pcm, ExecutionPolicy.ON_COMPLETE, completion, PapiReferenceResolver.empty());
            assertNotNull(resolved);
            assertFalse(resolved.isNoOp());
            assertEquals(ExecuteAs.PLAYER, resolved.executeAs());
            assertEquals(5, resolved.delayTicks());
            assertEquals("cond1", resolved.sourceId());
            assertNotNull(resolved.provenance());
            assertEquals(ActionTrustLevel.TRUSTED_PRESET, resolved.provenance().trustLevel());
            assertFalse(resolved.provenance().consoleDelegated());
            assertEquals("cond1", resolved.provenance().sourceId());
        }

        @Test
        @DisplayName("Selects ifFalse branch when condition evaluates to false")
        void selectsIfFalseBranch() {
            var cond = ConditionCompiler.compile("{0} equals \"yes\"", ConditionCompileOptions.forPreset());
            var trueAction = TrustedPresetAction.of("say accepted {player}", ExecuteAs.PLAYER, 5);
            var falseAction = TrustedPresetAction.of("say rejected {player}", ExecuteAs.CONSOLE, 10);
            var def = new ConditionalPostCommandDefinition("cond1", cond, ExecutionPolicy.ON_COMPLETE, trueAction, falseAction);

            var snapshot = new PresetSnapshot(Map.of(), Map.of(), Map.of(), Map.of("cond1", def), 1L);
            var completion = createCompletion(List.of("no"), snapshot);
            var pcm = new PostCommandMeta("cond1", new int[]{}, 0, false, DispatchTarget.PLAYER, true);

            var resolved = PostActionResolver.resolve(pcm, ExecutionPolicy.ON_COMPLETE, completion, PapiReferenceResolver.empty());
            assertNotNull(resolved);
            assertFalse(resolved.isNoOp());
            assertEquals(ExecuteAs.CONSOLE, resolved.executeAs());
            assertEquals(10, resolved.delayTicks());
            assertEquals("cond1", resolved.sourceId());
            assertNotNull(resolved.provenance());
            assertEquals(ActionTrustLevel.TRUSTED_PRESET, resolved.provenance().trustLevel());
            assertTrue(resolved.provenance().consoleDelegated());
            assertEquals("cond1", resolved.provenance().sourceId());
        }

        @Test
        @DisplayName("Absent branch evaluates to successful no-op")
        void absentBranchReturnsNoOp() {
            var cond = ConditionCompiler.compile("{0} equals \"yes\"", ConditionCompileOptions.forPreset());
            var trueAction = TrustedPresetAction.of("say accepted", ExecuteAs.PLAYER, 0);
            // ifFalseAction is null
            var def = new ConditionalPostCommandDefinition("cond_noop", cond, ExecutionPolicy.ON_COMPLETE, trueAction, null);

            var snapshot = new PresetSnapshot(Map.of(), Map.of(), Map.of(), Map.of("cond_noop", def), 1L);
            var completion = createCompletion(List.of("no"), snapshot);
            var pcm = new PostCommandMeta("cond_noop", new int[]{}, 0, false, DispatchTarget.PLAYER, true);

            var resolved = PostActionResolver.resolve(pcm, ExecutionPolicy.ON_COMPLETE, completion, PapiReferenceResolver.empty());
            assertNotNull(resolved);
            assertTrue(resolved.isNoOp());
        }

        @Test
        @DisplayName("Numeric condition comparison evaluates correctly")
        void numericConditionEvaluation() {
            var cond = ConditionCompiler.compile("{0} >= 100", ConditionCompileOptions.forPreset());
            var trueAction = TrustedPresetAction.of("say big amount", ExecuteAs.CONSOLE, 0);
            var falseAction = TrustedPresetAction.of("say small amount", ExecuteAs.CONSOLE, 0);
            var def = new ConditionalPostCommandDefinition("cond_num", cond, ExecutionPolicy.ON_COMPLETE, trueAction, falseAction);

            var snapshot = new PresetSnapshot(Map.of(), Map.of(), Map.of(), Map.of("cond_num", def), 1L);
            var pcm = new PostCommandMeta("cond_num", new int[]{}, 0, false, DispatchTarget.PLAYER, true);

            // 150 >= 100 -> true
            var comp1 = createCompletion(List.of("150"), snapshot);
            var res1 = PostActionResolver.resolve(pcm, ExecutionPolicy.ON_COMPLETE, comp1, PapiReferenceResolver.empty());
            assertNotNull(res1);
            assertEquals("say big amount", res1.template().source());

            // 50 >= 100 -> false
            var comp2 = createCompletion(List.of("50"), snapshot);
            var res2 = PostActionResolver.resolve(pcm, ExecutionPolicy.ON_COMPLETE, comp2, PapiReferenceResolver.empty());
            assertNotNull(res2);
            assertEquals("say small amount", res2.template().source());
        }

        @Test
        @DisplayName("PAPI in condition evaluates correctly with resolver")
        void papiInConditionEvaluation() {
            var cond = ConditionCompiler.compile("%vault_eco_balance% >= 500", ConditionCompileOptions.forPreset());
            var trueAction = TrustedPresetAction.of("say rich", ExecuteAs.CONSOLE, 0);
            var falseAction = TrustedPresetAction.of("say poor", ExecuteAs.CONSOLE, 0);
            var def = new ConditionalPostCommandDefinition("cond_papi", cond, ExecutionPolicy.ON_COMPLETE, trueAction, falseAction);

            var snapshot = new PresetSnapshot(Map.of(), Map.of(), Map.of(), Map.of("cond_papi", def), 1L);
            var completion = createCompletion(List.of(), snapshot);
            var pcm = new PostCommandMeta("cond_papi", new int[]{}, 0, false, DispatchTarget.PLAYER, true);

            // balance = 1000 -> true
            var richResolver = PapiReferenceResolver.fromMap(Map.of("vault_eco_balance", "1000"));
            var res1 = PostActionResolver.resolve(pcm, ExecutionPolicy.ON_COMPLETE, completion, richResolver);
            assertNotNull(res1);
            assertEquals("say rich", res1.template().source());

            // balance = 200 -> false
            var poorResolver = PapiReferenceResolver.fromMap(Map.of("vault_eco_balance", "200"));
            var res2 = PostActionResolver.resolve(pcm, ExecutionPolicy.ON_COMPLETE, completion, poorResolver);
            assertNotNull(res2);
            assertEquals("say poor", res2.template().source());
        }

        @Test
        @DisplayName("Missing PAPI in condition fails closed")
        void missingPapiFailsClosed() {
            var cond = ConditionCompiler.compile("%missing_token% equals \"abc\"", ConditionCompileOptions.forPreset());
            var trueAction = TrustedPresetAction.of("say ok", ExecuteAs.CONSOLE, 0);
            var def = new ConditionalPostCommandDefinition("cond_papi_miss", cond, ExecutionPolicy.ON_COMPLETE, trueAction, null);

            var snapshot = new PresetSnapshot(Map.of(), Map.of(), Map.of(), Map.of("cond_papi_miss", def), 1L);
            var completion = createCompletion(List.of(), snapshot);
            var pcm = new PostCommandMeta("cond_papi_miss", new int[]{}, 0, false, DispatchTarget.PLAYER, true);

            var ex = assertThrows(
                    PostActionResolutionException.class,
                    () -> PostActionResolver.resolve(pcm, ExecutionPolicy.ON_COMPLETE, completion, PapiReferenceResolver.empty())
            );
            assertEquals(DispatchErrorKind.INVALID_REQUEST, ex.getError().kind());
        }

        @Test
        @DisplayName("PAPI exceeding 1024 characters in condition fails closed")
        void papiOverlimitFailsClosed() {
            var cond = ConditionCompiler.compile("%large_token% equals \"abc\"", ConditionCompileOptions.forPreset());
            var trueAction = TrustedPresetAction.of("say ok", ExecuteAs.CONSOLE, 0);
            var def = new ConditionalPostCommandDefinition("cond_papi_big", cond, ExecutionPolicy.ON_COMPLETE, trueAction, null);

            var snapshot = new PresetSnapshot(Map.of(), Map.of(), Map.of(), Map.of("cond_papi_big", def), 1L);
            var completion = createCompletion(List.of(), snapshot);
            var pcm = new PostCommandMeta("cond_papi_big", new int[]{}, 0, false, DispatchTarget.PLAYER, true);

            String giant = "a".repeat(1025);
            var resolver = PapiReferenceResolver.fromMap(Map.of("large_token", giant));

            var ex = assertThrows(
                    PostActionResolutionException.class,
                    () -> PostActionResolver.resolve(pcm, ExecutionPolicy.ON_COMPLETE, completion, resolver)
            );
            assertEquals(DispatchErrorKind.INVALID_REQUEST, ex.getError().kind());
        }

        @Test
        @DisplayName("PAPI containing C0 control character in condition fails closed")
        void papiControlCharacterFailsClosed() {
            var cond = ConditionCompiler.compile("%bad_token% equals \"abc\"", ConditionCompileOptions.forPreset());
            var trueAction = TrustedPresetAction.of("say ok", ExecuteAs.CONSOLE, 0);
            var def = new ConditionalPostCommandDefinition("cond_papi_ctrl", cond, ExecutionPolicy.ON_COMPLETE, trueAction, null);

            var snapshot = new PresetSnapshot(Map.of(), Map.of(), Map.of(), Map.of("cond_papi_ctrl", def), 1L);
            var completion = createCompletion(List.of(), snapshot);
            var pcm = new PostCommandMeta("cond_papi_ctrl", new int[]{}, 0, false, DispatchTarget.PLAYER, true);

            var resolver = PapiReferenceResolver.fromMap(Map.of("bad_token", "abc\u0000def"));

            var ex = assertThrows(
                    PostActionResolutionException.class,
                    () -> PostActionResolver.resolve(pcm, ExecutionPolicy.ON_COMPLETE, completion, resolver)
            );
            assertEquals(DispatchErrorKind.INVALID_REQUEST, ex.getError().kind());
        }

        @Test
        @DisplayName("FLOW-11 injection payload in answer is treated strictly as data")
        void flow11InjectionInAnswerIsData() {
            var cond = ConditionCompiler.compile("{0} equals \"<!cmd> ; /op hacker\"", ConditionCompileOptions.forPreset());
            var trueAction = TrustedPresetAction.of("say match", ExecuteAs.CONSOLE, 0);
            var def = new ConditionalPostCommandDefinition("cond_flow11", cond, ExecutionPolicy.ON_COMPLETE, trueAction, null);

            var snapshot = new PresetSnapshot(Map.of(), Map.of(), Map.of(), Map.of("cond_flow11", def), 1L);
            var completion = createCompletion(List.of("<!cmd> ; /op hacker"), snapshot);
            var pcm = new PostCommandMeta("cond_flow11", new int[]{}, 0, false, DispatchTarget.PLAYER, true);

            var resolved = PostActionResolver.resolve(pcm, ExecutionPolicy.ON_COMPLETE, completion, PapiReferenceResolver.empty());
            assertNotNull(resolved);
            assertFalse(resolved.isNoOp());
            assertEquals("say match", resolved.template().source());
        }
    }
}
