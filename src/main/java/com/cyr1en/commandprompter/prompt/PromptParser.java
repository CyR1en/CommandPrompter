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
import com.cyr1en.commandprompter.hook.hooks.PapiHook;
import com.cyr1en.kiso.utils.SRegex;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

            var arg = resolveArg(prompt);
            plugin.getPluginLogger().debug("Argument in prompt: " + arg);

            var pcmKey = PromptQueueArgument.POST_COMMAND.getKey();
            plugin.getPluginLogger().debug("Checking equality: '" + arg + "' == '" + pcmKey + "'");
            if (pcmKey.equals(arg)) {
                var pcm = parsePCM(prompt);
                var promptQueue = manager.getPromptRegistry().get(promptContext.getSender());
                promptQueue.addPCM(pcm);
                // remove the <-exa some command>
                promptQueue.setCommand(promptQueue.getCommand().replace(prompt, ""));
                continue;
            }

            Class<? extends Prompt> pClass = manager.get(arg);
            plugin.getPluginLogger().debug("Prompt to construct: " + pClass.getSimpleName());
            try {
                var sender = promptContext.getSender();
                var promptArgs = ArgumentUtil.findPattern(PromptArgument.class, cleanPrompt(prompt));

                // Set papi placeholders if exists
                var promptTxt = ArgumentUtil.stripArgs(cleanPrompt(prompt));
                promptTxt = resolvePapiPlaceholders((Player) sender, promptTxt);

                var p = pClass.getConstructor(CommandPrompter.class, PromptContext.class,
                                String.class, List.class)
                        .newInstance(plugin, promptContext, promptTxt, promptArgs);
                manager.getPromptRegistry().addPrompt(sender, p);
            } catch (NoSuchMethodException | InvocationTargetException
                     | InstantiationException | IllegalAccessException e) {
                plugin.getPluginLogger().err("Error parsing prompt: " + prompt);
                plugin.getPluginLogger().err("Cause: " + e.getCause());
            }
        }
        return manager.getPromptRegistry().get(promptContext.getSender()).hashCode();
    }

    private String resolvePapiPlaceholders(Player sender, String prompt) {
        if (plugin.getHookContainer().isHooked(PapiHook.class)) return prompt;
        var papiHook = plugin.getHookContainer().getHook(PapiHook.class);
        return papiHook.get().setPlaceholder(sender, prompt);
    }

    private String resolveArg(String prompt) {
        sRegex.find(manager.getArgumentPattern(), prompt);
        var arg = sRegex.getResultsList().isEmpty() ? "" : getCleanArg(sRegex.getResultsList().get(0));

        if (arg.isBlank() || arg.isEmpty()) {
            var foundArgs = ArgumentUtil.findPattern(PromptQueueArgument.class, prompt);
            arg = foundArgs.isEmpty() ? arg : foundArgs.get(0).getKey();
        }
        return arg;
    }

    private String cleanPrompt(String prompt) {
        // keys to add for the argument pattern
        var keys = Arrays.stream(PromptQueueArgument.values()).map(Keyable::getCleanKey).toArray(String[]::new);
        var clean = prompt.substring(1, prompt.length() - 1)
                .replaceAll(manager.getArgumentPattern(keys).toString(), "");
        plugin.getPluginLogger().debug("Cleaned prompt: " + clean);
        return clean;
    }

    private String getCleanArg(String arg) {
        return arg.replaceAll("\\W", "");
    }

    private List<String> getPrompts(PromptContext promptContext) {
        return sRegex.find(Pattern.compile(escapedRegex), promptContext.getContent()).getResultsList();
    }

    private static final Pattern PCM_INDEX_PATTERN = Pattern.compile("p:\\d+");

    private PromptQueue.PostCommandMeta parsePCM(String prompt) {
        plugin.getPluginLogger().debug("Parsing PCM: " + prompt);
        var matcher = PCM_INDEX_PATTERN.matcher(prompt);
        var count = 0;
        while (matcher.find()) count++;

        plugin.getPluginLogger().debug("Index Count: " + count);
        var indexes = new int[count];

        matcher.reset();
        count = 0;
        while (matcher.find()) {
            var index = matcher.group().split(":")[1];
            indexes[count] = Integer.parseInt(index);
            count++;
        }
        var pcm = new PromptQueue.PostCommandMeta(cleanPrompt(prompt), indexes);
        plugin.getPluginLogger().debug("Parsed PCM: " + pcm);
        return pcm;
    }

    public interface Keyable {
        String getKey();

        String getCleanKey();
    }

    public enum PromptArgument implements Keyable {
        DISABLE_SANITATION("-ds"),
        INTEGER("-int"),
        STRING("-str");

        private final String key;

        PromptArgument(String key) {
            this.key = key.endsWith(" ") ? key : key + " ";
        }

        public String getKey() {
            return this.key;
        }

        public String getCleanKey() {
            return this.key.trim().replaceAll("-", "");
        }
    }

    public enum PromptQueueArgument implements Keyable {
        POST_COMMAND("-exa");

        private final String key;

        PromptQueueArgument(String key) {
            this.key = key.endsWith(" ") ? key : key + " ";
        }

        public String getKey() {
            return this.key;
        }

        public String getCleanKey() {
            return this.key.trim().replaceAll("-", "");
        }
    }

    public static class ArgumentUtil {

        /**
         * Function that takes in a cleaned (without prompts identifier) and finds every
         * prompt argument that is on the prompt.
         *
         * @param prompt String prompt to process
         * @return List of all argument found
         */
        public static <T extends Enum<T> & Keyable> List<T> findPattern(Class<T> anEnum, String prompt) {
            return Arrays.stream(anEnum.getEnumConstants())
                    .filter(arg -> Pattern.compile(arg.getKey()).matcher(prompt).find()).collect(Collectors.toList());
        }

        public static String stripArgs(String prompt) {
            var str = prompt;
            for (PromptParser.PromptArgument value : PromptParser.PromptArgument.values())
                str = str.replaceAll(value.getKey(), "");
            return str;
        }

    }
}
