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
import com.cyr1en.commandprompter.prompt.PromptContext;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;

import java.util.Arrays;
import java.util.List;

public class ChatPrompt extends AbstractPrompt {

    public ChatPrompt(CommandPrompter plugin, PromptContext context, String prompt) {
        super(plugin, context, prompt);
    }

    public void sendPrompt() {
        List<String> parts = Arrays.stream(getPrompt().split("\\{br}")).map(String::trim).toList();
        String prefix = getPlugin().getConfiguration().promptPrefix();
        parts.forEach(part -> getContext().getSender().sendMessage(color(prefix + part)));
        var isSendCancel = getPlugin().getPromptConfig().sendCancelText();
        getPlugin().getPluginLogger().debug("Send Cancel: " + isSendCancel);
        if (isSendCancel)
            sendCancelText();
    }

    private void sendCancelText() {
        try {
            if (Class.forName("org.spigotmc.SpigotConfig") == null)
                return;
            var cancelMessage = getPlugin().getPromptConfig().textCancelMessage();
            var hoverMessage = getPlugin().getPromptConfig().textCancelHoverMessage();
            String prefix = getPlugin().getConfiguration().promptPrefix();
            var component = new ComponentBuilder(color(prefix + cancelMessage))
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cp cancel"))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(color(hoverMessage))))
                    .create();
            getContext().getSender().spigot().sendMessage(component);
        } catch (ClassNotFoundException e) {
            getPlugin().getPluginLogger().debug("ChatAPI not available, can't send clickable cancel");
        }

    }
}
