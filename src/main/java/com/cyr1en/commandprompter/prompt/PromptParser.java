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
import com.cyr1en.commandprompter.api.Dispatcher;
import com.cyr1en.commandprompter.api.prompt.InputValidator;
import com.cyr1en.commandprompter.api.prompt.Prompt;
import com.cyr1en.commandprompter.hook.hooks.PapiHook;
import com.cyr1en.commandprompter.prompt.validators.NoopValidator;
import com.cyr1en.commandprompter.util.MMUtil;
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
        if (plugin.getConfiguration().ignoreMiniMessage())
            prompts = MMUtil.filterOutMiniMessageTags(prompts);
        return !prompts.isEmpty();
    }

    /**
     * Parses a contents of {@link PromptContext}
     *
     * <p>
     * This method will parse the contents of the {@link PromptContext} and
     * create a {@link PromptQueue} for the sender. It will also parse the
     * {@link Prompt} and add it to the {@link PromptQueue}.
     *
     * <p>
     * This function returns the hashCode of the {@link PromptQueue} that was
     * created. This is used to retroactively cancel the prompt within a certain time.
     *
     * @param promptContext Context to parse
     * @return hashCode of the {@link PromptQueue} that was created.
     */
    public int parsePrompts(PromptContext promptContext) {
        var prompts = getPrompts(promptContext);
        prompts = MMUtil.filterOutMiniMessageTags(prompts);

        plugin.getPluginLogger().debug("Prompts: " + prompts);

        var command = promptContext.getContent().trim();
        plugin.getPluginLogger().debug("Command: " + command);
        manager.getPromptRegistry().initRegistryFor(promptContext, command, getEscapedRegex());

        for (String prompt : prompts) {
            plugin.getPluginLogger().debug("Parsing: " + prompt);

            var arg = resolveArg(prompt);
            plugin.getPluginLogger().debug("Argument in prompt: '" + arg + "'");

            var pcmKey = PromptQueueArgument.POST_COMMAND.getKey();

            plugin.getPluginLogger().debug("Checking equality: '" + arg + "' == '" + pcmKey + "'");
            if (pcmKey.equals(arg)) {
                var pcm = parsePCM(promptContext, prompt);
                var promptQueue = manager.getPromptRegistry().get(promptContext.getPromptedPlayer());
                promptQueue.addPCM(pcm);
                // remove the <-exa some command>
                promptQueue.setCommand(promptQueue.getCommand().replace(prompt, ""));
                continue;
            }

            Class<? extends Prompt> pClass = manager.get(arg);
            if (pClass == null) {
                plugin.getPluginLogger().debug("Prompt not found for: " + arg);
                continue;
            }
            promptContext.setPromptKey(arg);
            plugin.getPluginLogger().debug("Prompt to construct: " + pClass.getSimpleName());
            try {
                var cleanPrompt = cleanPrompt(prompt);
                var sender = promptContext.getPromptedPlayer();
                var promptArgs = ArgumentUtil.findPattern(PromptArgument.class, cleanPrompt);
                plugin.getPluginLogger().debug("Prompt args: " + promptArgs);
                var inputValidator = extractInputValidation(cleanPrompt, (Player) sender);

                // Set papi placeholders if exists
                var promptTxt = ArgumentUtil.stripArgs(cleanPrompt);
                plugin.getPluginLogger().debug("Prompt stripped: " + promptTxt);
                promptTxt = resolvePapiPlaceholders((Player) sender, promptTxt);

                var p = pClass.getConstructor(CommandPrompter.class, PromptContext.class,
                                String.class, List.class)
                        .newInstance(plugin, promptContext, promptTxt, promptArgs);

                p.setInputValidator(inputValidator);
                if (promptArgs.contains(PromptArgument.DISABLE_SANITATION))
                    p.setInputSanitization(false);

                manager.getPromptRegistry().addPrompt(sender, p);
            } catch (NoSuchMethodException | InvocationTargetException
                     | InstantiationException | IllegalAccessException e) {
                plugin.getPluginLogger().err("Error parsing prompt: " + prompt);
                plugin.getPluginLogger().err("Cause: " + e.getCause());
            }
        }
        return manager.getPromptRegistry().get(promptContext.getPromptedPlayer()).hashCode();
    }

    private InputValidator extractInputValidation(String prompt, Player player) {
        // iv is with pattern -iv:<alias>
        var pattern = Pattern.compile(PromptArgument.INPUT_VALIDATION.getKey());
        var matcher = pattern.matcher(prompt);
        if (!matcher.find()) return new NoopValidator();
        var found = matcher.group();
        var alias = found.split(":")[1];
        return plugin.getPromptConfig().getInputValidator(alias, player);
    }

    private String resolvePapiPlaceholders(Player sender, String prompt) {
        if (!plugin.getHookContainer().isHooked(PapiHook.class)) return prompt;
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
        return arg.replace("-", "").trim();
    }

    private List<String> getPrompts(PromptContext promptContext) {
        return sRegex.find(Pattern.compile(escapedRegex), promptContext.getContent()).getResultsList();
    }

    // Pattern to check for prompt indices within a prompt that we can use for PCM.
    private static final Pattern PCM_INDEX_PATTERN = Pattern.compile("p:\\d+");
    // This checks if PCM has a delay (piped or not piped)
    private static final Pattern PCM_DELAYED_PATTERN = Pattern.compile("(exa|exac):\\d+(\\|[pc]|)");
    // This checks if PCM is on cancel
    private static final Pattern PCM_CANCEL_PATTERN = Pattern.compile("exac(:\\d+)?");
    // This checks if PCM is piped
    private static final Pattern PCM_PIPED_PATTERN = Pattern.compile("(exa|exac)(:\\d+|)\\|[pc]");

    private PromptQueue.PostCommandMeta parsePCM(PromptContext ctx, String prompt) {
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

        var delayMatcher = PCM_DELAYED_PATTERN.matcher(prompt);
        var pipeMatcher = PCM_PIPED_PATTERN.matcher(prompt);
        var isOnCancel = PCM_CANCEL_PATTERN.matcher(prompt).find();
        var delay = delayMatcher.find() ? Integer.parseInt(delayMatcher.group().split(":")[1]) : 0;
        var pipeTo = pipeMatcher.find() ? pipeMatcher.group().split("\\|")[1] : "";

        var dispatcherType = Dispatcher.Type.parse(pipeTo);
        if (dispatcherType != Dispatcher.Type.PASSTHROUGH && ctx.getCommandSender() instanceof Player) {
            dispatcherType = Dispatcher.Type.PASSTHROUGH;
            plugin.getPluginLogger().warn("Players cannot pipe post commands to a different executor, defaulting to PASSTHROUGH.");
        }
        var pcm = new PromptQueue.PostCommandMeta(cleanPrompt(prompt), indexes, delay, isOnCancel, dispatcherType);
        plugin.getPluginLogger().debug("Parsed PCM: " + pcm);
        return pcm;
    }

    public interface Keyable {
        String getKey();

        String getCleanKey();
    }

    public enum PromptArgument implements Keyable {
        DISABLE_SANITATION("-ds"),
        INPUT_VALIDATION("-iv:\\w+", false),
        INTEGER("-int"),
        STRING("-str");

        private final String key;

        PromptArgument(String key, boolean appendSpace) {
            this.key = appendSpace ? (key.endsWith(" ") ? key : key + " ") : key;
        }

        PromptArgument(String key) {
            this(key, true);
        }

        public String getKey() {
            return this.key;
        }

        public String getCleanKey() {
            return this.key.trim().replaceAll("-", "");
        }
    }

    public enum PromptQueueArgument implements Keyable {
        POST_COMMAND("-(((exac)|(exa))+)(?::(\\d+))?(\\|[pc])?");

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
            for (PromptParser.PromptArgument value : PromptParser.PromptArgument.values()) {
                var pattern = Pattern.compile(value.getKey());
                str = pattern.matcher(str).replaceAll("").trim();
            }
            return str;
        }

    }
}
