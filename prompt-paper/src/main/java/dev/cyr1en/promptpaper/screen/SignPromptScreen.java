package dev.cyr1en.promptpaper.screen;

import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.config.PromptConfig;
import dev.cyr1en.promptui.ComponentUtil;
import dev.cyr1en.promptui.InputScreen;
import dev.cyr1en.promptui.ScreenProvider;
import dev.cyr1en.promptui.ScreenResult;
import dev.cyr1en.promptui.SignInputScreen;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.entity.Player;

/**
 * Prompt screen that presents a sign GUI for text input, supporting single-line and multi-arg modes
 * with configurable input-field placement.
 */
public class SignPromptScreen extends AbstractWrapperPromptScreen {

  private String[] promptLines;
  private boolean multiArg;
  private final dev.cyr1en.promptpaper.preset.SignPrompt signPrompt;

  public SignPromptScreen(
      CommandPrompter plugin,
      Player player,
      dev.cyr1en.promptpaper.preset.SignPrompt signPrompt,
      List<ScreenProvider> providers) {
    super(plugin, player, signPrompt.promptText(), providers);
    this.signPrompt = signPrompt;
  }

  /**
   * Arranges prompt lines into sign slots and tries each {@link ScreenProvider} to create a sign
   * screen, falling back to chat if none succeed.
   */
  @Override
  public void open() {
    var promptConfig = plugin.getConfigLoader().getPromptConfig();
    var parts = displayText.split("\\{br\\}");
    this.multiArg = Arrays.stream(parts).anyMatch(p -> p.matches("[\\S]+:"));
    var config = buildConfig(promptConfig);

    String[] arranged;
    if (!signPrompt.id().startsWith("inline-") && !signPrompt.defaultLines().isEmpty()) {
      arranged = new String[4];
      Arrays.fill(arranged, "");
      for (int i = 0; i < Math.min(4, signPrompt.defaultLines().size()); i++) {
        arranged[i] = signPrompt.defaultLines().get(i);
      }
    } else if (multiArg) {
      arranged = new String[4];
      Arrays.fill(arranged, "");
      System.arraycopy(parts, 0, arranged, 0, Math.min(parts.length, 4));
    } else {
      int lines = Math.min(parts.length, 3);
      arranged = arrangeLines(parts, lines, promptConfig.inputFieldLocation());
    }
    this.promptLines = arranged;

    plugin
        .getPluginLogger()
        .debug(
            "Opening sign prompt for "
                + player.getName()
                + " multiArg="
                + multiArg
                + " location="
                + promptConfig.inputFieldLocation());

    for (var provider : providers) {
      InputScreen candidate = null;
      try {
        plugin
            .getPluginLogger()
            .debug("Attempting sign provider: " + provider.getClass().getSimpleName());
        var nms = provider.createSign(plugin, player, arranged);
        candidate = nms;
        if (nms instanceof SignInputScreen signScreen) {
          signScreen.configure(config);
          signScreen.onResult(this::handleResult);
          this.wrapped = signScreen;
          this.open = true;
          signScreen.onOpenFailure(failure -> handleAsyncProviderFailure(signScreen, failure));
          signScreen.open();
          plugin
              .getPluginLogger()
              .debug("Sign provider succeeded: " + provider.getClass().getSimpleName());
          return;
        }
      } catch (Throwable t) {
        open = false;
        if (candidate != null) {
          try {
            candidate.close();
          } catch (Throwable closeFailure) {
            plugin
                .getPluginLogger()
                .debug("Sign provider cleanup failed: " + closeFailure.getMessage());
          }
        }
        plugin
            .getPluginLogger()
            .debug(
                "Sign provider "
                    + provider.getClass().getSimpleName()
                    + " failed: "
                    + t.getMessage());
      }
    }
    plugin.getPluginLogger().debug("All sign providers failed, falling back to chat");
    wrapped = fallbackToChat();
    open = true;
    try {
      wrapped.open();
    } catch (Throwable failure) {
      open = false;
      plugin
          .getPluginLogger()
          .warn("Chat fallback for sign prompt failed: " + failure.getMessage());
    }
  }

  private void handleAsyncProviderFailure(SignInputScreen failed, Throwable failure) {
    if (!open || wrapped != failed) return;
    plugin.getPluginLogger().debug("Asynchronous sign provider failed: " + failure.getMessage());
    open = false;
    try {
      failed.close();
    } catch (Throwable closeFailure) {
      plugin.getPluginLogger().debug("Sign provider cleanup failed: " + closeFailure.getMessage());
    }
    try {
      var fallback = fallbackToChat();
      wrapped = fallback;
      open = true;
      fallback.open();
    } catch (Throwable fallbackFailure) {
      open = false;
      plugin
          .getPluginLogger()
          .warn("Chat fallback for sign prompt failed: " + fallbackFailure.getMessage());
    }
  }

  private Map<String, String> buildConfig(PromptConfig cfg) {
    var config = new HashMap<String, String>();
    config.put("signMaterial", cfg.signMaterial());
    return config;
  }

  /**
   * Distributes prompt parts across the 4 sign lines according to the configured input-field
   * location (top, bottom, etc.).
   */
  static String[] arrangeLines(String[] parts, int partCount, String location) {
    var result = new String[4];
    Arrays.fill(result, "");
    int count = Math.min(partCount, 3);
    var promptParts = Arrays.copyOfRange(parts, 0, Math.min(parts.length, count));

    String loc = location == null ? "bottom" : location.toLowerCase();
    switch (loc) {
      case "top" ->
          System.arraycopy(promptParts, 0, result, 1, Math.min(count, promptParts.length));
      case "top-aggregate" -> {
        int promptStart = 4 - count;
        System.arraycopy(promptParts, 0, result, promptStart, Math.min(count, promptParts.length));
      }
      case "bottom-aggregate" ->
          System.arraycopy(promptParts, 0, result, 0, Math.min(count, promptParts.length));
      default -> System.arraycopy(promptParts, 0, result, 0, Math.min(count, promptParts.length));
    }
    return result;
  }

  /**
   * Processes sign lines — in multi-arg mode, extracts labeled values; in single mode, filters out
   * prompt lines or extracts based on input field location — then forwards the result.
   */
  @Override
  protected void handleResult(ScreenResult result) {
    handleResult(result, false);
  }

  @Override
  protected void handleFallbackResult(ScreenResult result) {
    handleResult(result, true);
  }

  private void handleResult(ScreenResult result, boolean chatFallback) {
    if (!open) return;
    open = false;
    if (callback == null) return;
    if (result.cancelled()) {
      plugin.getPluginLogger().debug("Sign result cancelled for " + player.getName());
      callback.accept(result);
      return;
    }

    var lines = result.answer().split("\n", -1);
    for (int i = 0; i < lines.length; i++)
      lines[i] = (signPrompt.sanitize() ? ComponentUtil.stripColor(lines[i]) : lines[i]).trim();

    String processed;
    if (chatFallback) {
      processed = String.join(" ", lines);
    } else if (multiArg) {
      var parts = new ArrayList<String>();
      for (var line : lines) {
        if (line.matches("[\\S]+:.*")) parts.add(line.split(":", 2)[1].trim());
      }
      processed = String.join(" ", parts);
    } else if (!signPrompt.id().startsWith("inline-") && !signPrompt.defaultLines().isEmpty()) {
      processed = joinAnswerLines(lines, 0, lines.length);
    } else {
      if (lines.length < 4) {
        processed = joinAnswerLines(lines, 0, lines.length);
      } else {
        var promptConfig = plugin.getConfigLoader().getPromptConfig();
        String location =
            promptConfig.inputFieldLocation() == null
                ? "bottom"
                : promptConfig.inputFieldLocation().toLowerCase();
        var parts = displayText.split("\\{br\\}");
        int promptPartsCount = Math.min(parts.length, 3);
        switch (location) {
          case "top" -> processed = lines[0];
          case "bottom" -> processed = lines[3];
          case "top-aggregate" -> {
            int promptStart = 4 - promptPartsCount;
            processed = joinAnswerLines(lines, 0, promptStart);
          }
          case "bottom-aggregate" -> processed = joinAnswerLines(lines, promptPartsCount, 4);
          default -> processed = lines[3];
        }
      }
    }

    plugin
        .getPluginLogger()
        .debug("Sign result for " + player.getName() + ": multiArg=" + multiArg);

    if (processed.isEmpty()) {
      callback.accept(ScreenResult.cancel());
      return;
    }

    if (processed.equalsIgnoreCase(plugin.getConfigLoader().getConfig().cancelKeyword())) {
      callback.accept(ScreenResult.cancel());
      return;
    }

    callback.accept(ScreenResult.answer(processed));
  }

  private String joinAnswerLines(String[] lines, int fromIndex, int toIndex) {
    var answers = new ArrayList<String>();
    int end = Math.min(toIndex, lines.length);
    for (int i = Math.max(fromIndex, 0); i < end; i++) {
      var line = lines[i];
      if (!line.isEmpty() && !matchesPromptLine(i, line)) {
        answers.add(line);
      }
    }
    return String.join(" ", answers);
  }

  private boolean matchesPromptLine(int index, String line) {
    if (promptLines == null || index >= promptLines.length) return false;
    var promptLine =
        signPrompt.sanitize()
            ? ComponentUtil.stripColor(promptLines[index]).trim()
            : promptLines[index].trim();
    return promptLine.equals(line);
  }
}
