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
import com.cyr1en.commandprompter.hook.hooks.CarbonChatHook;
import com.cyr1en.commandprompter.hook.hooks.PuerkasChatHook;
import com.cyr1en.commandprompter.prompt.PromptContext;
import com.cyr1en.commandprompter.prompt.PromptManager;
import com.cyr1en.commandprompter.prompt.PromptParser;
import com.cyr1en.commandprompter.unsafe.PvtFieldMutator;
import es.capitanpuerka.puerkaschat.manager.PuerkasFormat;
import fr.euphyllia.energie.model.SchedulerType;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.RegisteredListener;
import org.fusesource.jansi.Ansi;

import java.util.*;

public class ChatPrompt extends AbstractPrompt {

    public ChatPrompt(CommandPrompter plugin, PromptContext context, String prompt,
                      List<PromptParser.PromptArgument> args) {
        super(plugin, context, prompt, args);
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
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/commandprompter cancel"))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(color(hoverMessage))))
                    .create();
            getContext().getSender().spigot().sendMessage(component);
        } catch (ClassNotFoundException e) {
            getPlugin().getPluginLogger().debug("ChatAPI not available, can't send clickable cancel");
        }
    }

    public static void resolveListener(CommandPrompter plugin) {
        var container = plugin.getHookContainer();
        var ccHook = container.getHook(CarbonChatHook.class);
        ccHook.ifHooked((e) -> {
                    var res = e.subscribe();
                    if (!res) {
                        registerDefault(plugin);
                        plugin.getPluginLogger().info("Using default listener");
                        return;
                    }
                    plugin.getPluginLogger().info("Using CarbonChat listener");
                })
                .orElse(() -> {
                    registerDefault(plugin);
                    plugin.getPluginLogger().info("Using default listener");
                }).complete();
    }

    private static void registerDefault(CommandPrompter plugin) {
        var defaultListener = new DefaultListener(plugin.getPromptManager(), plugin);
        Bukkit.getPluginManager().registerEvents(defaultListener, plugin);
        DefaultListener.setPriority(plugin);
    }

    public static class ResponseHandler {
        private final CommandPrompter plugin;
        private final PromptManager manager;

        public ResponseHandler(CommandPrompter plugin) {
            this.plugin = plugin;
            this.manager = plugin.getPromptManager();
        }

        public void onResponse(Player player, String msg, Cancellable event) {
            plugin.getPluginLogger().debug("Cancellable event: " + event.getClass().getSimpleName());
            msg = msg.replace("§", "&");
            if (!manager.getPromptRegistry().inCommandProcess(player))
                return;
            event.setCancelled(true);
            var message = ChatColor.stripColor(
                    ChatColor.translateAlternateColorCodes('&', msg));
            var cancelKeyword = plugin.getConfiguration().cancelKeyword();

            if (cancelKeyword.equalsIgnoreCase(message))
                manager.cancel(player);

            var queue = manager.getPromptRegistry().get(player);
            if (Objects.isNull(queue))
                return;

            var ctx = new PromptContext.Builder()
                    .setCancellable(event)
                    .setSender(player)
                    .setContent(msg).build();

            CommandPrompter.getScheduler().runTask(SchedulerType.SYNC, task -> manager.processPrompt(ctx));
        }
    }

    public static class DefaultListener implements Listener {

        private final PromptManager manager;
        private final CommandPrompter plugin;
        private final ResponseHandler handler;

        public DefaultListener(PromptManager manager, CommandPrompter plugin) {
            this.manager = manager;
            this.plugin = plugin;
            this.handler = new ResponseHandler(plugin);
        }

        public PromptManager getManager() {
            return this.manager;
        }

        @EventHandler(priority = EventPriority.LOWEST)
        public void onChat(AsyncPlayerChatEvent event) {
            var plain = event.getMessage();
            var isPuerkasChatHooked = plugin.getHookContainer().getHook(PuerkasChatHook.class).isHooked();
            if (!isPuerkasChatHooked) {
                handler.onResponse(event.getPlayer(), plain, event);
            } else if ((PuerkasFormat.getFormats() != null && !PuerkasFormat.getFormats().isEmpty()))
                handler.onResponse(event.getPlayer(), plain, event);
        }

        public static void setPriority(CommandPrompter plugin) {
            var configPriority = plugin.getPromptConfig().responseListenerPriority().toUpperCase(Locale.ROOT);
            if (configPriority.equals("DEFAULT"))
                return;

            listAllRegisteredListeners(plugin);
            var logger = plugin.getPluginLogger();
            var currentPriority = getCurrentEventPriority(plugin);
            if (Objects.isNull(currentPriority))
                return;

            var priority = EventPriority.LOWEST;
            try {
                priority = EventPriority.valueOf(configPriority);
            } catch (IllegalArgumentException ignore) {
                logger.err("Could not set '%s' as priority for PromptResponseListener. Defaulted to '%s'",
                        configPriority, priority.name());
            }
            // Do nothing if current priority = config priority
            if (currentPriority.name().equals(priority.name()))
                return;

            setPriority(plugin, priority);
        }

        private static synchronized void setPriority(CommandPrompter plugin, EventPriority newPriority) {
            var logger = plugin.getPluginLogger();
            logger.debug("Setting PromptResponseListener priority from '%s' to '%s'",
                    getCurrentEventPriority(plugin).name(), newPriority.name());
            var handlerList = AsyncPlayerChatEvent.getHandlerList();
            try {
                var handlerSlotsF = handlerList.getClass().getDeclaredField("handlerslots");
                handlerSlotsF.setAccessible(true);
                @SuppressWarnings("unchecked")
                var handlerSlots = (EnumMap<EventPriority, ArrayList<RegisteredListener>>) handlerSlotsF
                        .get(handlerList);
                var currentPriority = getCurrentEventPriority(plugin);

                var registeredListener = handlerSlots.get(currentPriority).stream()
                        .filter(rL -> rL.getListener().getClass().equals(DefaultListener.class))
                        .findFirst().orElseThrow();

                handlerList.unregister(registeredListener);

                var mutator = new PvtFieldMutator();
                mutator.forField("priority").in(registeredListener).replaceWith(newPriority);

                handlerList.register(registeredListener);
                handlerList.bake();
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }

            logger.info("PromptResponsePriority is now '%s'",
                    new Ansi().fgRgb(153, 214, 90).a(getCurrentEventPriority(plugin).name()));
            listAllRegisteredListeners(plugin);
        }

        private static EventPriority getCurrentEventPriority(CommandPrompter plugin) {
            for (RegisteredListener registeredListener : AsyncPlayerChatEvent.getHandlerList()
                    .getRegisteredListeners()) {
                if (registeredListener.getPlugin().getName().equals(plugin.getName()))
                    return registeredListener.getPriority();
            }
            return null;
        }

        private static void listAllRegisteredListeners(CommandPrompter plugin) {
            var logger = plugin.getPluginLogger();
            logger.debug("Registered Listeners: ");
            for (RegisteredListener registeredListener : AsyncPlayerChatEvent.getHandlerList()
                    .getRegisteredListeners()) {
                logger.debug("  - '%s'", registeredListener.getListener().getClass().getSimpleName());
                logger.debug("      Priority: " + registeredListener.getPriority());
                logger.debug("      Plugin: " + registeredListener.getPlugin().getName());
            }
        }

    }
}
