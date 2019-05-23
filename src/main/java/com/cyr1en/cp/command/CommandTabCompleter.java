/*
 * MIT License
 *
 * Copyright (c) 2019 Ethan Bacurio
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

package com.cyr1en.cp.command;

import com.cyr1en.cp.CommandPrompter;
import com.google.common.collect.ImmutableList;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class CommandTabCompleter implements TabCompleter {

  private List<AbstractCommand> commands;

  public CommandTabCompleter(CommandPrompter plugin) {
    this.commands = plugin.getCommandManager().getCommands();
  }

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
    int argLength = args.length;
    if (argLength == 0)
      return commands.stream().map(AbstractCommand::getName).collect(Collectors.toList());
    else if (argLength == 1)
      return getMatch(sender, args[0]);
    return getInnerMatch(args).orElse(ImmutableList.of());
  }

  private Optional<List<String>> getInnerMatch(String[] args) {
    //omit index[0] because inner #onCommandCompelete doesn't need it.
    String[] shortened = Arrays.copyOfRange(args, 1, args.length);
    Optional<AbstractCommand> cmd = commands.stream().filter(c -> c.getName().equalsIgnoreCase(args[0])).findFirst();
    AtomicReference<List<String>> aList = new AtomicReference<>(ImmutableList.of());
    cmd.ifPresent(c -> aList.set(c.onTabComplete(shortened)));
    return Optional.of(aList.get());
  }

  private List<String> getMatch(CommandSender commandSender, String query) {
    List<String> exact = new ArrayList<>();
    List<String> wrongcase = new ArrayList<>();
    List<String> startswith = new ArrayList<>();
    commands.forEach(cmd -> {
      String name = cmd.getName();

      List<String> lowerCaseAliases = cmd.getAlias();
      lowerCaseAliases.replaceAll(String::toLowerCase);

      if (name.equals(query)) {
        if (commandSender.hasPermission(cmd.getPermission()))
          exact.add(name);
      } else if ((name.equalsIgnoreCase(query)) && exact.isEmpty()) {
        if (commandSender.hasPermission(cmd.getPermission()))
          wrongcase.add(name);
      } else if ((name.toLowerCase().startsWith(query.toLowerCase())) && wrongcase.isEmpty()) {
        if (commandSender.hasPermission(cmd.getPermission()))
          startswith.add(name);
      }
    });
    if (!exact.isEmpty())
      return Collections.unmodifiableList(exact);
    if (!wrongcase.isEmpty())
      return Collections.unmodifiableList(wrongcase);
    if (!startswith.isEmpty())
      return Collections.unmodifiableList(startswith);
    return ImmutableList.of();
  }
}
