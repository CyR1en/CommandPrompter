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

package com.cyr1en.commandprompter.prompt.prompts;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.api.prompt.Prompt;
import com.cyr1en.commandprompter.prompt.PromptContext;
import com.cyr1en.commandprompter.prompt.PromptManager;

public abstract class AbstractPrompt implements Prompt {

    private final CommandPrompter plugin;
    private final PromptContext context;
    private final String prompt;
    private final PromptManager promptManager;

    public AbstractPrompt(CommandPrompter plugin, PromptContext context, String prompt) {
        this.plugin = plugin;
        this.context = context;
        this.prompt = prompt;
        this.promptManager = plugin.getPromptManager();
    }

    @Override
    public abstract void sendPrompt();

    @Override
    public PromptContext getContext() {
        return context;
    }

    @Override
    public CommandPrompter getPlugin() {
        return plugin;
    }

    @Override
    public String getPrompt() {
        return prompt;
    }

    @Override
    public PromptManager getPromptManager() {
        return promptManager;
    }
}
