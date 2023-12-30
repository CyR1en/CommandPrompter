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

package com.cyr1en.commandprompter.api.prompt;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.prompt.PromptContext;
import com.cyr1en.commandprompter.prompt.PromptManager;
import com.cyr1en.commandprompter.prompt.PromptParser;

import java.util.List;

public interface Prompt {

    /**
     * Method that sends the prompt.
     */
    void sendPrompt();

    /**
     * Accessor for the {@link PromptContext}.
     *
     * <p>{@link PromptContext} contains all the information that you need for
     * the {@link Prompt}.</p>
     *
     * @return context for the prompt.
     * @see PromptContext
     */
    PromptContext getContext();

    /**
     * Get instance of the plugin.
     *
     * <p>The instance is a sub-class {@link org.bukkit.plugin.java.JavaPlugin}.</p>
     *
     * @return Instance of a plugin.
     */
    CommandPrompter getPlugin();

    /**
     * Get the actual prompt to send
     */
    String getPrompt();

    /**
     * Get prompt manager
     */
    PromptManager getPromptManager();

    List<PromptParser.PromptArgument> getArgs();
    
    /**
     * Set the input validator
     *
     * @param inputValidator input validator to check prompt input
     */
    void setInputValidator(InputValidator inputValidator);

    /**
     * Get the input validator
     *
     * @return input validator to check prompt input
     */
    InputValidator getInputValidator();

    /**
     * Returns a boolean value if inputs should be sanitized.
     *
     * @return true if inputs should be sanitized
     */
    boolean sanitizeInput();

    /**
     * Set whether inputs should be sanitized.
     *
     * @param sanitize true if inputs should be sanitized
     */
    void setInputSanitization(boolean sanitize);
}
