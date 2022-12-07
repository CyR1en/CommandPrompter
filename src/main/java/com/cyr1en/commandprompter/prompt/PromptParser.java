/*
 * MIT License
 *
 * Copyright (c) 2020 Ethan Bacurio
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.cyr1en.commandprompter.prompt;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.api.prompt.Prompt;
import com.cyr1en.kiso.utils.SRegex;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.regex.Pattern;

public class PromptParser {

    private static final String DEFAULT_REGEX = "<.*?>";

    private final CommandPrompter plugin;
    private final SRegex sRegex;
    private final PromptManager manager;
    private String escapedRegex;

    public PromptParser(PromptManager promptManager) {
        this.plugin = promptManager.getPlugin();
        this.escapedRegex = DEFAULT_REGEX;
        this.manager = promptManager;
        this.sRegex = new SRegex();
        initRegex();
    }

    public void initRegex() {
        var regex = plugin.getConfiguration().argumentRegex();
        regex = regex.trim();
        this.escapedRegex = escapeRegex(regex);
    }

    private String escapeRegex(String regex) {
        var escapedRegex = (String.valueOf(regex.charAt(0))).replaceAll("[^\\w\\s]", "\\\\$0") +
                (regex.substring(1, regex.length() - 1)) +
                (String.valueOf(regex.charAt(regex.length() - 1))).replaceAll("[^\\w\\s]", "\\\\$0");
        plugin.getPluginLogger().debug("Regex: " + regex);
        return escapedRegex;
    }

    public String getEscapedRegex() {
        return escapedRegex;
    }

    public boolean isParsable(PromptContext promptContext) {
        var prompts = getPrompts(promptContext);
        return !prompts.isEmpty();
    }

    /**
     * Parses a contents of {@link PromptContext}
     *
     * @param promptContext Context to parse
     * @return hashCode of the {@link PromptQueue} that was created.
     */
    public int parsePrompts(PromptContext promptContext) {
        var prompts = getPrompts(promptContext);
        plugin.getPluginLogger().debug("Prompts: " + prompts);

        var command = promptContext.getContent().trim();
        plugin.getPluginLogger().debug("Command: " + command);
        manager.getPromptRegistry().initRegistryFor(promptContext, command, getEscapedRegex());

        for (String prompt : prompts) {
            plugin.getPluginLogger().debug("Parsing: " + prompt);

            sRegex.find(manager.getArgumentPattern(), prompt);
            var arg = sRegex.getResultsList().isEmpty() ? "" : getCleanArg(sRegex.getResultsList().get(0));
            plugin.getPluginLogger().debug("Argument in prompt: " + arg);

            Class<? extends Prompt> pClass = manager.get(arg);
            plugin.getPluginLogger().debug("Prompt to construct: " + pClass.getSimpleName());
            try {
                var sender = promptContext.getSender();
                var p = pClass.getConstructor(CommandPrompter.class, PromptContext.class, String.class)
                        .newInstance(plugin, promptContext, cleanPrompt(prompt));
                manager.getPromptRegistry().addPrompt(sender, p);
            } catch (NoSuchMethodException | InvocationTargetException
                     | InstantiationException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return manager.getPromptRegistry().get(promptContext.getSender()).hashCode();
    }

    private String cleanPrompt(String prompt) {
        var clean = prompt.substring(1, prompt.length() - 1)
                .replaceAll(manager.getArgumentPattern().toString(), "");
        plugin.getPluginLogger().debug("Cleaned prompt: " + clean);
        return clean;
    }

    private String getCleanArg(String arg) {
        return arg.replaceAll("\\W", "");
    }

    private List<String> getPrompts(PromptContext promptContext) {
        return sRegex.find(Pattern.compile(escapedRegex), promptContext.getContent()).getResultsList();
    }
}
