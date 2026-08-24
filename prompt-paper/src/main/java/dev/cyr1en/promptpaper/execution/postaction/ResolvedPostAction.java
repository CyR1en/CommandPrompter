package dev.cyr1en.promptpaper.execution.postaction;

import dev.cyr1en.promptpaper.execution.dispatch.ActionProvenance;
import dev.cyr1en.promptpaper.execution.postaction.template.CompiledActionTemplate;
import dev.cyr1en.promptpaper.preset.ExecuteAs;
import java.util.Objects;

/**
 * Internal representation of a resolved post-action ready for execution.
 */
record ResolvedPostAction(
        CompiledActionTemplate template,
        ExecuteAs executeAs,
        int delayTicks,
        ActionProvenance provenance,
        String sourceId,
        boolean isNoOp
) {
    public ResolvedPostAction {
        if (!isNoOp) {
            Objects.requireNonNull(template, "template must not be null for active action");
            Objects.requireNonNull(executeAs, "executeAs must not be null for active action");
            Objects.requireNonNull(provenance, "provenance must not be null for active action");
        }
    }

    static ResolvedPostAction of(
            CompiledActionTemplate template,
            ExecuteAs executeAs,
            int delayTicks,
            ActionProvenance provenance,
            String sourceId
    ) {
        return new ResolvedPostAction(template, executeAs, delayTicks, provenance, sourceId, false);
    }

    static ResolvedPostAction noOp() {
        return new ResolvedPostAction(null, ExecuteAs.PLAYER, 0, ActionProvenance.untrustedInline(), null, true);
    }
}
