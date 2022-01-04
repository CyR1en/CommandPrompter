package com.cyr1en.commandprompter.prompt.prompts;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.prompt.PromptContext;
import com.cyr1en.commandprompter.prompt.ui.SignMenuFactory;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class SignPrompt extends AbstractPrompt {

    private SignMenuFactory signMenuFactory;

    public SignPrompt(CommandPrompter plugin, PromptContext context, String prompt) {
        super(plugin, context, prompt);
        this.signMenuFactory = new SignMenuFactory(plugin);
    }

    @Override
    public void sendPrompt() {
        List<String> parts = Arrays.asList(getPrompt().split("\\{br}"));
        if (parts.size() > 3)
            parts = parts.subList(0, 2);
        List<String> finalParts = parts.stream().map(this::stripColor).toList();
        signMenuFactory.newMenu(parts)
                .response((p, s) -> {
                    getPlugin().getPluginLogger().debug("Sign Strings: " + Arrays.toString(s));
                    var list = Arrays.stream(s).toList().stream().filter(str -> !str.isBlank()).toList();

                    // If the sign contains the same message as the prompt
                    // we'll consider the command completion cancelled.
                    if (list.equals(finalParts)) {
                        getPromptManager().cancel(p);
                        return true;
                    }
                    var message = stripColor(list.get(list.size() - 1));
                    getPlugin().getPluginLogger().debug("Response: " + message);
                    var cancelKeyword = getPlugin().getConfiguration().cancelKeyword();
                    if (cancelKeyword.equalsIgnoreCase(message)) {
                        getPromptManager().cancel(p);
                        return true;
                    }
                    var ctx = new PromptContext(null, p, message);
                    getPromptManager().processPrompt(ctx);
                    return true;
                })
                .open((Player) getContext().getSender());
    }
}
