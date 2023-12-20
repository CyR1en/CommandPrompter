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

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * A helper class that holds context for the prompt system.
 *
 * <p>
 * We can think of it as a data container for various functions throughout the
 * prompt system.
 */
public class PromptContext {
    private final Cancellable cancellable;
    private final CommandSender sender;
    private String content;
    private String promptKey;
    private boolean isConsoleDelegate;

    private final String paKey;

    public PromptContext(PlayerCommandPreprocessEvent e) {
        this(e, e.getPlayer(), e.getMessage(), null, null, false);
    }

    public PromptContext(@Nullable Cancellable callable,
                         Player sender,
                         String content,
                         @Nullable String promptKey,
                         @Nullable String paKey,
                         boolean isConsoleDelegate) {
        this.cancellable = callable;
        this.sender = sender;
        this.content = content;
        this.promptKey = promptKey;
        this.paKey = paKey;
        this.isConsoleDelegate = isConsoleDelegate;
    }

    public CommandSender getSender() {
        return sender;
    }

    public Cancellable getCancellable() {
        return cancellable;
    }

    public String getContent() {
        return content;
    }

    public String getPromptKey() {
        return promptKey;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setPromptKey(String promptKey) {
        this.promptKey = promptKey;
    }

    public void setIsConsoleDelegate(boolean b) {
        this.isConsoleDelegate = b;
    }

    public boolean isConsoleDelegate() {
        return this.isConsoleDelegate;
    }

    public String getPaKey() {
        return Objects.isNull(paKey) ? "" : paKey;
    }

    @Override
    public String toString() {
        return "PromptContext{" +
                "cancellable=" + cancellable +
                ", sender=" + sender +
                ", content='" + content + '\'' +
                ", promptKey='" + promptKey + '\'' +
                ", paKey='" + paKey + '\'' +
                ", isConsoleDelegate=" + isConsoleDelegate +
                '}';
    }

    // Builder for PromptContext
    public static class Builder {
        private Cancellable cancellable;
        private CommandSender sender;
        private String content;

        private String promptKey;

        private String paKey;
        private boolean isConsoleDelegate;

        public Builder() {
            this.cancellable = null;
            this.sender = null;
            this.content = null;
            this.promptKey = null;
            this.paKey = null;
            this.isConsoleDelegate = false;
        }

        public Builder setCancellable(Cancellable cancellable) {
            this.cancellable = cancellable;
            return this;
        }

        public Builder setSender(CommandSender sender) {
            this.sender = sender;
            return this;
        }

        public Builder setContent(String content) {
            this.content = content;
            return this;
        }

        public Builder setConsoleDelegate(boolean isConsoleDelegate) {
            this.isConsoleDelegate = isConsoleDelegate;
            return this;
        }

        public Builder setPromptKey(String promptKey) {
            this.promptKey = promptKey;
            return this;
        }

        public Builder setPaKey(String paKey) {
            this.paKey = paKey;
            return this;
        }

        public PromptContext build() {
            // check if sender and content is null.
            if (sender == null || content == null)
                throw new IllegalStateException("Sender and content must not be null!");
            return new PromptContext(cancellable, (Player) sender,
                    content, promptKey, paKey, isConsoleDelegate);
        }
    }
}
