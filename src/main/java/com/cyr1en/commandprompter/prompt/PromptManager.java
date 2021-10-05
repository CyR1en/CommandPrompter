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
import com.google.common.collect.ImmutableList;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class PromptManager {

  private List<Prompt> registeredPrompts;
  private CommandPrompter plugin;

  private PromptManager(List<Prompt> registeredPrompts) {
    this.registeredPrompts = registeredPrompts;
  }

  public PromptQueue parseCommand(PromptContext context) {
    SRegex simpleRegex = new SRegex(context.getContent());
    String regex = plugin.getConfiguration().getString("Argument-Regex").trim();
    String parsedEscapedRegex = (String.valueOf(regex.charAt(0))).replaceAll("[^\\w\\s]", "\\\\$0") +
            (regex.substring(1, regex.length() - 1)) +
            (String.valueOf(regex.charAt(regex.length() - 1))).replaceAll("[^\\w\\s]", "\\\\$0");
    simpleRegex.find(Pattern.compile(parsedEscapedRegex));

    List<String> prompts = simpleRegex.getResultsList();
    ImmutableList.Builder<Prompt> imBuilder = ImmutableList.builder();
    for (String strPrompt : prompts)
      parsePrompt(strPrompt).ifPresent(imBuilder::add);

    return new PromptQueue(context);
  }

  private Optional<Prompt> parsePrompt(String stringPrompt) {
    SRegex simpleRegex = new SRegex(stringPrompt);
    simpleRegex.find(Pattern.compile("-\\s"));
    //TODO: Finish implementation 6/26/20
    return Optional.empty();
  }

  public void processPrompt(PromptContext context) {

  }

  private void getArguments(String promptSegment) {

  }

  public static class Builder {
    private List<Prompt> registeredPrompt;
    private CommandPrompter plugin;

    public Builder(CommandPrompter plugin) {
      this.plugin = plugin;
      registeredPrompt = new LinkedList<>();
    }

    public Builder addPrompt(Prompt prompt) {
      registeredPrompt.add(prompt);
      return this;
    }

    public PromptManager build() {
      return new PromptManager(this.registeredPrompt);
    }
  }
}
