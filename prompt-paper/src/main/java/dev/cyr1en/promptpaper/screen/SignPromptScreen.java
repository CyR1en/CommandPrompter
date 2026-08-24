package dev.cyr1en.promptpaper.screen;

import dev.cyr1en.promptui.ScreenProvider;
import dev.cyr1en.promptui.ScreenResult;
import dev.cyr1en.promptui.SignInputScreen;
import dev.cyr1en.promptui.InputScreen;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.config.PromptConfig;
import dev.cyr1en.promptui.ComponentUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.entity.Player;

/**
 * Prompt screen that presents a sign GUI for text input, supporting
 * single-line and multi-arg modes with configurable input-field placement.
 */
public class SignPromptScreen extends AbstractWrapperPromptScreen {

    private String[] promptLines;
    private boolean multiArg;
    private final dev.cyr1en.promptpaper.preset.SignPrompt signPrompt;

    public SignPromptScreen(CommandPrompter plugin, Player player, dev.cyr1en.promptpaper.preset.SignPrompt signPrompt, List<ScreenProvider> providers) {
        super(plugin, player, signPrompt.promptText(), providers);
        this.signPrompt = signPrompt;
    }

    /**
     * Arranges prompt lines into sign slots and tries each {@link ScreenProvider}
     * to create a sign screen, falling back to chat if none succeed.
     */
    @Override
    public void open() {
        var promptConfig = plugin.getConfigLoader().getPromptConfig();
        var parts = displayText.split("\\{br\\}");
        this.multiArg = Arrays.stream(parts).anyMatch(p -> p.matches("[\\S]+:"));
        var config = buildConfig(promptConfig);

        String[] arranged;
        if (signPrompt != null && !signPrompt.id().startsWith("inline-") && !signPrompt.defaultLines().isEmpty()) {
            arranged = new String[4];
            Arrays.fill(arranged, "");
            for (int i = 0; i < Math.min(4, signPrompt.defaultLines().size()); i++) {
                arranged[i] = signPrompt.defaultLines().get(i);
            }
        } else {
            int lines = Math.min(parts.length, multiArg ? 4 : 3);
            arranged = arrangeLines(parts, lines, promptConfig.inputFieldLocation());
        }
        this.promptLines = arranged;

        plugin.getPluginLogger().debug("Opening sign prompt for " + player.getName()
                + " multiArg=" + multiArg
                + " location=" + promptConfig.inputFieldLocation());

        for (var provider : providers) {
            InputScreen candidate = null;
            try {
                plugin.getPluginLogger().debug("Attempting sign provider: "
                        + provider.getClass().getSimpleName());
                var nms = provider.createSign(plugin, player, arranged);
                candidate = nms;
                if (nms instanceof SignInputScreen signScreen) {
                    signScreen.configure(config);
                    signScreen.onResult(this::handleResult);
                    this.wrapped = signScreen;
                    this.open = true;
                    signScreen.onOpenFailure(failure -> handleAsyncProviderFailure(signScreen, failure));
                    signScreen.open();
                    plugin.getPluginLogger().debug("Sign provider succeeded: "
                            + provider.getClass().getSimpleName());
                    return;
                }
            } catch (Throwable t) {
                open = false;
                if (candidate != null) {
                    try {
                        candidate.close();
                    } catch (Throwable closeFailure) {
                        plugin.getPluginLogger().debug("Sign provider cleanup failed: "
                                + closeFailure.getMessage());
                    }
                }
                plugin.getPluginLogger().debug("Sign provider "
                        + provider.getClass().getSimpleName() + " failed: " + t.getMessage());
            }
        }
        plugin.getPluginLogger().debug("All sign providers failed, falling back to chat");
        wrapped = fallbackToChat();
        open = true;
        try {
            wrapped.open();
        } catch (Throwable failure) {
            open = false;
            plugin.getPluginLogger().warn("Chat fallback for sign prompt failed: "
                    + failure.getMessage());
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
            plugin.getPluginLogger().warn("Chat fallback for sign prompt failed: "
                    + fallbackFailure.getMessage());
        }
    }

    private Map<String, String> buildConfig(PromptConfig cfg) {
        var config = new HashMap<String, String>();
        config.put("signMaterial", cfg.signMaterial());
        return config;
    }

    /**
     * Distributes prompt parts across the 4 sign lines according to
     * the configured input-field location (top, bottom, etc.).
     */
    static String[] arrangeLines(String[] parts, int partCount, String location) {
        var result = new String[4];
        Arrays.fill(result, "");
        int count = Math.min(partCount, 3);
        var promptParts = Arrays.copyOfRange(parts, 0, Math.min(parts.length, count));

        String loc = location == null ? "bottom" : location.toLowerCase();
        switch (loc) {
            case "top" -> {
                result[0] = "";
                System.arraycopy(promptParts, 0, result, 1, Math.min(count, promptParts.length));
            }
            case "top-aggregate" -> {
                int promptStart = 4 - count;
                System.arraycopy(promptParts, 0, result, promptStart, Math.min(count, promptParts.length));
                for (int i = 0; i < promptStart; i++) result[i] = "";
            }
            case "bottom-aggregate" -> {
                System.arraycopy(promptParts, 0, result, 0, Math.min(count, promptParts.length));
                for (int i = count; i < 4; i++) result[i] = "";
            }
            default -> {
                System.arraycopy(promptParts, 0, result, 0, Math.min(count, promptParts.length));
                result[3] = "";
            }
        }
        return result;
    }

    /**
     * Processes sign lines — in multi-arg mode, extracts labeled values;
     * in single mode, filters out prompt lines or extracts based on input field location — then forwards the result.
     */
    @Override
    protected void handleResult(ScreenResult result) {
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
        if (multiArg) {
            var parts = new ArrayList<String>();
            for (var line : lines) {
                if (line.matches("[\\S]+:.*"))
                    parts.add(line.split(":", 2)[1].trim());
            }
            processed = String.join(" ", parts);
        } else if (signPrompt != null && !signPrompt.id().startsWith("inline-") && !signPrompt.defaultLines().isEmpty()) {
            var filtered = new ArrayList<String>();
            for (int i = 0; i < lines.length; i++) {
                var line = lines[i];
                if (line.isEmpty()) continue;
                boolean isPromptLine = promptLines != null && i < promptLines.length
                        && (signPrompt.sanitize()
                            ? ComponentUtil.stripColor(promptLines[i]).trim().equals(line)
                            : promptLines[i].trim().equals(line));
                if (!isPromptLine) filtered.add(line);
            }
            processed = String.join(" ", filtered);
        } else {
            if (lines.length < 4) {
                var filtered = new ArrayList<String>();
                for (int i = 0; i < lines.length; i++) {
                    var line = lines[i];
                    if (line.isEmpty()) continue;
                    boolean isPromptLine = promptLines != null && i < promptLines.length
                            && (signPrompt.sanitize()
                                ? ComponentUtil.stripColor(promptLines[i]).trim().equals(line)
                                : promptLines[i].trim().equals(line));
                    if (!isPromptLine) filtered.add(line);
                }
                processed = String.join(" ", filtered);
            } else {
                var promptConfig = plugin.getConfigLoader().getPromptConfig();
                String location = promptConfig.inputFieldLocation() == null ? "bottom" : promptConfig.inputFieldLocation().toLowerCase();
                var parts = displayText.split("\\{br\\}");
                int promptPartsCount = Math.min(parts.length, 3);
                switch (location) {
                    case "top" -> processed = lines[0];
                    case "bottom" -> processed = lines[3];
                    case "top-aggregate" -> {
                        int promptStart = 4 - promptPartsCount;
                        var filtered = new ArrayList<String>();
                        for (int i = 0; i < promptStart; i++) {
                            var line = lines[i];
                            if (line.isEmpty()) continue;
                            boolean isPromptLine = promptLines != null && i < promptLines.length
                                    && (signPrompt.sanitize()
                                        ? ComponentUtil.stripColor(promptLines[i]).trim().equals(line)
                                        : promptLines[i].trim().equals(line));
                            if (!isPromptLine) filtered.add(line);
                        }
                        processed = String.join(" ", filtered);
                    }
                    case "bottom-aggregate" -> {
                        var filtered = new ArrayList<String>();
                        for (int i = promptPartsCount; i < 4; i++) {
                            var line = lines[i];
                            if (line.isEmpty()) continue;
                            boolean isPromptLine = promptLines != null && i < promptLines.length
                                    && (signPrompt.sanitize()
                                        ? ComponentUtil.stripColor(promptLines[i]).trim().equals(line)
                                        : promptLines[i].trim().equals(line));
                            if (!isPromptLine) filtered.add(line);
                        }
                        processed = String.join(" ", filtered);
                    }
                    default -> processed = lines[3];
                }
            }
        }

        plugin.getPluginLogger().debug("Sign result for " + player.getName()
                + ": multiArg=" + multiArg);

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
}
