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
import com.cyr1en.commandprompter.prompt.PromptContext;
import com.cyr1en.commandprompter.prompt.PromptManager;
import com.cyr1en.commandprompter.prompt.PromptParser;
import com.cyr1en.commandprompter.util.ServerUtil;
import com.cyr1en.commandprompter.util.unsafe.PvtFieldMutator;
import net.md_5.bungee.api.chat.*;
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
        if (ServerUtil.BUNGEE_CHAT_AVAILABLE())
            sendWithChatAPI(parts);
        else
            sendWithDefault(parts);
    }

    private void sendWithDefault(List<String> parts) {
        var prefix = getPlugin().getConfiguration().promptPrefix();
        var cancelText = makeCancelButton();
        if (parts.size() == 1) {
            getContext().getPromptedPlayer().sendMessage(color(prefix + parts.get(0)));
            if (!Arrays.equals(cancelText, new BaseComponent[0]))
                getContext().getPromptedPlayer().spigot().sendMessage(cancelText);
            return;
        }
        parts.forEach(part -> getContext().getPromptedPlayer().sendMessage(color(prefix + part)));
        getPlugin().getPluginLogger().debug("Cancel component length: " + cancelText.length);
        if (Arrays.equals(cancelText, new BaseComponent[0]))
            return;
        getContext().getPromptedPlayer().spigot().sendMessage(cancelText);
    }

    private void sendWithChatAPI(List<String> parts) {
        var prefix = getPlugin().getConfiguration().promptPrefix();
        if (parts.size() == 1) {
            var msg = color(prefix + parts.get(0) + " ");
            var component = new ComponentBuilder().append(TextComponent.fromLegacy(msg));
            var cancelComponent = makeCancelButton();
            if (!Arrays.equals(cancelComponent, new BaseComponent[0]))
                component.append(cancelComponent);
            getContext().getPromptedPlayer().spigot().sendMessage(component.create());
            return;
        }
        parts.forEach(part -> getContext().getPromptedPlayer().sendMessage(color(prefix + part)));
        var cancelComponent = makeCancelButton(true);
        getPlugin().getPluginLogger().debug("Cancel component length: " + cancelComponent.length);
        if (Arrays.equals(cancelComponent, new BaseComponent[0]))
            return;
        getContext().getPromptedPlayer().spigot().sendMessage(cancelComponent);
    }

    private BaseComponent[] makeCancelButton() {
        return makeCancelButton(false);
    }

    private BaseComponent[] makeCancelButton(boolean addPrefix) {
        if (!ServerUtil.BUNGEE_CHAT_AVAILABLE() || !getPlugin().getPromptConfig().sendCancelText())
            return new BaseComponent[0];

        var prefix = getPlugin().getConfiguration().promptPrefix();
        var cancelMessage = getPlugin().getPromptConfig().textCancelMessage();
        cancelMessage = addPrefix ? prefix + cancelMessage : cancelMessage;
        var hoverMessage = getPlugin().getPromptConfig().textCancelHoverMessage();

        return new ComponentBuilder(TextComponent.fromLegacy(color(cancelMessage)))
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/commandprompter cancel"))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(color(hoverMessage))))
                .create();
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
                    .setCommandSender(player)
                    .setPromptedPlayer(player)
                    .setContent(msg).build();

            Bukkit.getScheduler().runTask(plugin, () -> manager.processPrompt(ctx));
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
