package com.cyr1en.commandprompter.prompt.prompts;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.prompt.PromptContext;
import com.cyr1en.commandprompter.prompt.ui.SignMenuFactory;
import com.cyr1en.kiso.utils.FastStrings;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class SignPrompt extends AbstractPrompt {

    private static final String MULTI_ARG_PATTERN_EMPTY = "[ -~]+:";
    private static final String MULTI_ARG_PATTERN_FILLED = MULTI_ARG_PATTERN_EMPTY + "[ -~]+";

    private final SignMenuFactory signMenuFactory;
    private boolean isMultiArg;

    public SignPrompt(CommandPrompter plugin, PromptContext context, String prompt) {
        super(plugin, context, prompt);
        this.signMenuFactory = new SignMenuFactory(plugin);
        isMultiArg = false;
    }

    @Override
    public void sendPrompt() {
        List<String> parts = Arrays.asList(getPrompt().split("\\{br}"));
        checkMultiArg(parts);
        getPlugin().getPluginLogger().debug("Is Multi-Arg: " + isMultiArg);
        if (parts.size() > 3 && !isMultiArg)
            parts = parts.subList(0, 2);
        else if (parts.size() > 4)
            parts = parts.subList(0, 3);

        List<String> finalParts = parts;
        var menu = signMenuFactory.newMenu(parts)
                .response((p, s) -> process(finalParts, p, s));
        Bukkit.getScheduler().runTaskLater(getPlugin(), () -> {
            menu.open((Player) getContext().getSender());
            if (isMultiArg)
                getPlugin().getMessenger()
                        .sendMessage(getContext().getSender(), getPlugin().getI18N().getProperty("SignPromptMultiArg"));
            else
                getPlugin().getMessenger()
                        .sendMessage(getContext().getSender(), getPlugin().getI18N().getProperty("SignPromptReminder"));
        }, 2L);
    }

    private void checkMultiArg(List<String> parts) {
        isMultiArg = parts.stream().map(String::trim).anyMatch(s -> s.matches(MULTI_ARG_PATTERN_EMPTY));
    }

    private boolean process(List<String> parts, Player p, String[] s) {
        var cleanedParts = parts.stream().map(this::stripColor).toList();
        getPlugin().getPluginLogger().debug("Sign Strings: " + Arrays.toString(s));

        var response = isMultiArg ?
                FastStrings.join(Arrays.stream(s).filter(str -> !str.isBlank() && !cleanedParts.contains(str))
                        .filter(str -> str.matches(MULTI_ARG_PATTERN_FILLED))
                        .map(str -> str.replaceAll(MULTI_ARG_PATTERN_EMPTY, "").trim()).toArray(), " ") :
                FastStrings.join(Arrays.stream(s)
                        .filter(str -> !cleanedParts.contains(str) && !str.isBlank()).toArray(), " ");

        getPlugin().getPluginLogger().debug("Response: " + response);

        // If the sign contains the same message as the prompt
        // we'll consider the command completion cancelled.
        if (response.isBlank()) {
            getPromptManager().cancel(p);
            return true;
        }

        var cancelKeyword = getPlugin().getConfiguration().cancelKeyword();
        if (cancelKeyword.equalsIgnoreCase(response)) {
            getPromptManager().cancel(p);
            return true;
        }
        var ctx = new PromptContext(null, p, response);

        getPromptManager().processPrompt(ctx);

        return true;
    }
}
