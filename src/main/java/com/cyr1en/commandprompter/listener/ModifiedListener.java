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

package com.cyr1en.commandprompter.listener;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.unsafe.CommandDispatchEvent;
import com.cyr1en.commandprompter.unsafe.ModifiedCommandMap;
import com.cyr1en.commandprompter.unsafe.PvtFieldMutator;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

/**
 * Class that would listen to the customized event placed on the modified command map's
 * dispatch function.
 */
public class ModifiedListener extends CommandListener {

    /**
     * Default constructor
     *
     * <p>
     * This constructor would also execute the replacement of the field 'commandMap' in the
     * server class. This has to be done before any plugin is enabled to allow command registration
     * on the modified field.
     *
     * @param plugin Instance of the {@link CommandPrompter}
     */
    public ModifiedListener(CommandPrompter plugin) {
        super(plugin);
        try {
            logWarning();
            var mutator = new PvtFieldMutator();
            var newMap = new ModifiedCommandMap(plugin.getServer(), plugin);
            mutator.forField("commandMap").in(plugin.getServer()).replaceWith(newMap);
            plugin.getLogger().warning("Current command map: " +
                    mutator.forField("commandMap").in(plugin.getServer()).getClassName());
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private void logWarning() {
        plugin.getLogger().warning("Warning! CommandPrompter is now going to use the modified command map.");
        plugin.getLogger().warning("Changing the value of a private final variable can make your program unstable.");
        plugin.getLogger().warning("If you experience any problem, please disable this feature immediately!");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(CommandDispatchEvent event) {
        if (!(event.getSender() instanceof Player))
            return;
        plugin.getLogger().info("This is modified listener");
        //process((Player) event.getSender(), event, event.getCommandLine());
    }

}
