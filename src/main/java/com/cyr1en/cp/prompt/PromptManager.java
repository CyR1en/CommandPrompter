package com.cyr1en.cp.prompt;

import com.cyr1en.cp.CommandPrompter;
import com.cyr1en.kiso.utils.SRegex;
import com.google.common.collect.ImmutableList;
import com.sun.xml.internal.bind.v2.TODO;
import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

public class PromptManager {

  private List<Prompt> registeredPrompts;
  private CommandPrompter plugin;

  private PromptManager(List<Prompt> registeredPrompts) {
    this.registeredPrompts = registeredPrompts;
  }

  public List<Prompt> parseCommand(PromptContext context) {
    SRegex simpleRegex = new SRegex(context.getContent());
    String regex = plugin.getConfiguration().getString("Argument-Regex").trim();
    String parsedEscapedRegex = (String.valueOf(regex.charAt(0))).replaceAll("[^\\w\\s]", "\\\\$0") +
            (regex.substring(1, regex.length() - 1)) +
            (String.valueOf(regex.charAt(regex.length() - 1))).replaceAll("[^\\w\\s]", "\\\\$0");
    simpleRegex.find(Pattern.compile(parsedEscapedRegex));

    List<String> prompts = simpleRegex.getResultsList();
    ImmutableList.Builder<Prompt> imBuilder = ImmutableList.builder();
    for (String strPrompt : prompts)
      imBuilder.add(parsePrompt(strPrompt));

    return imBuilder.build();
  }

  private Prompt parsePrompt(String stringPrompt) {
    SRegex simpleRegex = new SRegex(stringPrompt);
    simpleRegex.find(Pattern.compile("-\\s"));
    //TODO: Finish implementation 6/26/20
    return null;
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
