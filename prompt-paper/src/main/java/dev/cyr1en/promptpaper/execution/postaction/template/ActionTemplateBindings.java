package dev.cyr1en.promptpaper.execution.postaction.template;

import dev.cyr1en.promptcore.logic.transform.TemplateBindings;
import java.util.List;

/** Immutable container for runtime values bound to action template placeholders. */
public final class ActionTemplateBindings implements TemplateBindings {

  private final List<String> answers;
  private final String playerName;
  private final PapiReferenceResolver papiResolver;

  private ActionTemplateBindings(
      List<String> answers, String playerName, PapiReferenceResolver papiResolver) {
    this.answers = answers == null ? List.of() : List.copyOf(answers);
    this.playerName = playerName;
    this.papiResolver = papiResolver;
  }

  public static ActionTemplateBindings of(
      List<String> answers, String playerName, PapiReferenceResolver papiResolver) {
    return new ActionTemplateBindings(answers, playerName, papiResolver);
  }

  public static ActionTemplateBindings of(List<String> answers, String playerName) {
    return new ActionTemplateBindings(answers, playerName, PapiReferenceResolver.empty());
  }

  public static ActionTemplateBindings ofAnswers(List<String> answers) {
    return new ActionTemplateBindings(answers, null, PapiReferenceResolver.empty());
  }

  public static ActionTemplateBindings empty() {
    return new ActionTemplateBindings(List.of(), null, PapiReferenceResolver.empty());
  }

  public List<String> answers() {
    return answers;
  }

  public String playerName() {
    return playerName;
  }

  public PapiReferenceResolver papiResolver() {
    return papiResolver;
  }

  @Override
  public String get(String key) {
    if (key == null) {
      return null;
    }
    if ("player".equals(key)) {
      return playerName;
    }
    if ("input".equals(key)) {
      return answers.isEmpty() ? null : answers.get(0);
    }
    try {
      int idx = Integer.parseInt(key);
      if (idx >= 0 && idx < answers.size()) {
        return answers.get(idx);
      }
    } catch (NumberFormatException ignored) {
    }
    return null;
  }
}
