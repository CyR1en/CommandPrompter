package dev.cyr1en.promptpaper.engine;

import dev.cyr1en.promptcore.PostCommandMeta;
import dev.cyr1en.promptcore.plan.ExecutionPlanDefinition;
import dev.cyr1en.promptpaper.preset.PresetSnapshot;
import java.util.List;
import java.util.Objects;

/**
 * Inception artifacts captured at session interception before publication.
 *
 * @param incarnation exact session incarnation token
 * @param generation session generation token at inception
 * @param planDefinition compiled execution plan definition
 * @param presetSnapshot immutable preset snapshot captured at inception
 * @param originalPostCommands immutable full PCM source list captured at inception
 */
public record SessionInceptionArtifacts(
    long incarnation,
    long generation,
    ExecutionPlanDefinition planDefinition,
    PresetSnapshot presetSnapshot,
    List<PostCommandMeta> originalPostCommands) {

  public SessionInceptionArtifacts {
    Objects.requireNonNull(presetSnapshot, "presetSnapshot must not be null");
    originalPostCommands =
        originalPostCommands != null ? List.copyOf(originalPostCommands) : List.of();
  }

  public SessionInceptionArtifacts(
      long incarnation,
      long generation,
      ExecutionPlanDefinition planDefinition,
      PresetSnapshot presetSnapshot) {
    this(incarnation, generation, planDefinition, presetSnapshot, List.of());
  }

  public SessionInceptionArtifacts withGeneration(long nextGeneration) {
    return new SessionInceptionArtifacts(
        this.incarnation,
        nextGeneration,
        this.planDefinition,
        this.presetSnapshot,
        this.originalPostCommands);
  }

  public boolean matches(long expectedIncarnation, long expectedGeneration) {
    return this.incarnation == expectedIncarnation
        && (expectedGeneration < 0 || this.generation == expectedGeneration);
  }
}
