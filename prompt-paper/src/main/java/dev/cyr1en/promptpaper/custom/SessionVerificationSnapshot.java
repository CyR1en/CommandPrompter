package dev.cyr1en.promptpaper.custom;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable snapshot contract carrying expected session state for post-scheduler-hop screen verification.
 *
 * @param playerUuid expected player UUID
 * @param expectedIncarnation expected session incarnation
 * @param expectedGeneration expected prompt generation
 * @param promptIndex expected prompt index in multi-prompt sequence (-1 if not applicable)
 * @param attemptToken unique screen-attempt sequence token
 */
public record SessionVerificationSnapshot(
        UUID playerUuid,
        long expectedIncarnation,
        long expectedGeneration,
        int promptIndex,
        long attemptToken
) {

    public SessionVerificationSnapshot {
        Objects.requireNonNull(playerUuid, "playerUuid");
    }

    /**
     * Convenience constructor omitting promptIndex (defaulting to -1).
     */
    public SessionVerificationSnapshot(
            UUID playerUuid,
            long expectedIncarnation,
            long expectedGeneration,
            long attemptToken
    ) {
        this(playerUuid, expectedIncarnation, expectedGeneration, -1, attemptToken);
    }
}
