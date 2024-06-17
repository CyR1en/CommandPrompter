package com.cyr1en.commandprompter.prompt.prompts;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.prompt.PromptContext;
import com.cyr1en.commandprompter.prompt.PromptParser;
import com.cyr1en.commandprompter.util.Util;
import com.cyr1en.kiso.utils.FastStrings;
import de.rapha149.signgui.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SignPrompt extends AbstractPrompt {

    private static final String MULTI_ARG_PATTERN_EMPTY = "[\\S+]+:";
    private static final String MULTI_ARG_PATTERN_FILLED = MULTI_ARG_PATTERN_EMPTY + "\\s?+[\\S+]+";

    private boolean isMultiArg;

    public SignPrompt(CommandPrompter plugin, PromptContext context, String prompt,
            List<PromptParser.PromptArgument> args) {
        super(plugin, context, prompt, args);
        isMultiArg = false;
    }

    @Override
    public void sendPrompt() {
        List<String> parts = Arrays.asList(getPrompt().split("\\{br}"));
        checkMultiArg(parts);
        getPlugin().getPluginLogger().debug("Is Multi-Arg: " + isMultiArg);
        if (parts.size() > 3 && !isMultiArg)
            parts = parts.subList(0, 3);
        else if (parts.size() > 4)
            parts = parts.subList(0, 4);

        List<String> finalParts = parts;

        var matStr = getPlugin().getPromptConfig().signMaterial();
        var mat = Util.getCheckedMaterial(matStr, Material.OAK_SIGN);
        getPlugin().getPluginLogger().debug("Material: " + mat.name());

        var gui = SignGUI.builder()
                .setLines(finalParts.toArray(String[]::new))
                .setType(mat)
                .setHandler((p, r) -> process(finalParts, p, r.getLines()))
                .build();

        gui.open((Player) getContext().getSender());

    }

    private void checkMultiArg(List<String> parts) {
        isMultiArg = parts.stream().map(String::trim).anyMatch(s -> s.matches(MULTI_ARG_PATTERN_EMPTY));
    }

    private List<SignGUIAction> process(List<String> parts, Player p, String[] s) {
        var cleanedParts = parts.stream().map(this::stripColor).toList();
        getPlugin().getPluginLogger().debug("Sign Strings: " + Arrays.toString(s));

        var response = isMultiArg
                ? FastStrings.join(Arrays.stream(s).filter(str -> !str.isBlank() && !cleanedParts.contains(str))
                        .filter(str -> str.matches(MULTI_ARG_PATTERN_FILLED))
                        .map(str -> str.replaceAll(MULTI_ARG_PATTERN_EMPTY, "").trim()).toArray(), " ")
                : FastStrings.join(Arrays.stream(s)
                        .filter(str -> !cleanedParts.contains(str) && !str.isBlank()).toArray(), " ");

        getPlugin().getPluginLogger().debug("Response: " + response);

        // If the sign contains the same message as the prompt
        // we'll consider the command completion cancelled.
        if (response.isBlank()) {
            getPromptManager().cancel(p);
            return Collections.emptyList();
        }

        var cancelKeyword = getPlugin().getConfiguration().cancelKeyword();
        if (cancelKeyword.equalsIgnoreCase(response)) {
            getPromptManager().cancel(p);
            return Collections.emptyList();
        }
        var ctx = new PromptContext.Builder()
                .setSender(p)
                .setContent(response).build();

        getPromptManager().processPrompt(ctx);

        return Collections.emptyList();
    }
}
