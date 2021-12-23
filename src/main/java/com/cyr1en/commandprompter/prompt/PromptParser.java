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
import com.cyr1en.commandprompter.prompt.impl.PlayerChatPrompt;
import com.cyr1en.kiso.utils.SRegex;
import org.bukkit.command.Command;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

public class PromptParser {

    private CommandPrompter plugin;
    private SRegex sRegex;
    private String escapedRegex;
    private PromptManager manager;

    public PromptParser(PromptManager promptManager) {
        this.plugin = promptManager.getPlugin();
        this.escapedRegex = getEscapedRegex();
        this.manager = promptManager;
        this.sRegex = new SRegex();
    }

    private String getEscapedRegex() {
        String regex = plugin.getConfiguration().argumentRegex();
        regex = regex.trim();
        return (String.valueOf(regex.charAt(0))).replaceAll("[^\\w\\s]", "\\\\$0") +
                (regex.substring(1, regex.length() - 1)) +
                (String.valueOf(regex.charAt(regex.length() - 1))).replaceAll("[^\\w\\s]", "\\\\$0");
    }

    public void parsePrompts(PromptContext promptContext) {
        List<String> prompts = getPrompts(promptContext);
        for (String prompt : prompts) {
            sRegex.find(manager.getArgumentPattern(), prompt);
            if (sRegex.getResultsList().isEmpty()) {
                manager.getPromptRegistry()
                        .put(promptContext.getSender(), new PlayerChatPrompt(plugin, promptContext));
                continue;
            }
            String argument = sRegex.getResultsList().get(0);
            Class<? extends Prompt> pClass= manager.get(argument.replaceAll("\\W", ""));
        }
    }

    private List<String> getPrompts(PromptContext promptContext) {
        return sRegex.find(Pattern.compile(escapedRegex), promptContext.getContent()).getResultsList();
    }
}
