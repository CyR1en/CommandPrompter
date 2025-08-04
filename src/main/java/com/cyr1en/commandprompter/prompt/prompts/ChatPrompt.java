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
import com.cyr1en.commandprompter.util.unsafe.PvtFieldMutator;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredListener;

import java.util.*;

import static com.cyr1en.commandprompter.util.AdventureUtil.*;

public class ChatPrompt extends AbstractPrompt {

    public ChatPrompt(CommandPrompter plugin, PromptContext context, String prompt,
            List<PromptParser.PromptArgument> args) {
        super(plugin, context, prompt, args);
    }

    public void sendPrompt() {
        List<String> parts = Arrays.stream(getPrompt().split("\\{br}")).map(String::trim).toList();
        send(parts);
    }

    private void send(List<String> parts) {
        var prefix = getPlugin().getConfiguration().promptPrefix();
        if (parts.size() == 1) {
            var msg = color(prefix + parts.getFirst() + " ");
            var cancelComponent = makeCancelButton();
            var component = cancelComponent.equals(Component.empty()) ? joinComponents(msg, cancelComponent) : msg;
            getContext().getPromptedPlayer().sendMessage(component);
            return;
        }
        parts.forEach(part -> getContext().getPromptedPlayer().sendMessage(color(prefix + part)));
        getContext().getPromptedPlayer().sendMessage(makeCancelButton(true));
    }

    private Component makeCancelButton() {
        return makeCancelButton(false);
    }

    private Component makeCancelButton(boolean addPrefix) {
        if (!getPlugin().getPromptConfig().sendCancelText())
            return Component.empty();

        var prefix = getPlugin().getConfiguration().promptPrefix();
        var cancelMessage = getPlugin().getPromptConfig().textCancelMessage();
        cancelMessage = addPrefix ? prefix + cancelMessage : cancelMessage;
        var hoverMessage = getPlugin().getPromptConfig().textCancelHoverMessage();

        return Component.text(cancelMessage)
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/commandprompter cancel"))
                .hoverEvent(HoverEvent.showText(Component.text(hoverMessage)));
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

        public void onResponse(Player player, Component msgComponent, Cancellable event) {
            plugin.getPluginLogger().debug("Cancellable event: " + event.getClass().getSimpleName());
            var msg = toLegacyColor(msgComponent);
            msg = msg.replace("§", "&");
            if (!manager.getPromptRegistry().inCommandProcess(player))
                return;
            event.setCancelled(true);
            var message = MiniMessage.miniMessage().deserialize(msg);
            var cancelKeyword = plugin.getConfiguration().cancelKeyword();

            if (cancelKeyword.equalsIgnoreCase(plain(message)))
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
        private final ResponseHandler handler;

        public DefaultListener(PromptManager manager, CommandPrompter plugin) {
            this.manager = manager;
            this.handler = new ResponseHandler(plugin);
        }

        public PromptManager getManager() {
            return this.manager;
        }

        @EventHandler(priority = EventPriority.LOWEST)
        public void onChat(AsyncChatEvent event) {
            var plain = event.message();
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
                logger.err("Could not set '{0}' as priority for PromptResponseListener. Defaulted to '{1}'",
                        configPriority, priority.name());
            }
            // Do nothing if current priority = config priority
            if (currentPriority.name().equals(priority.name()))
                return;

            setPriority(plugin, priority);
        }

        private static synchronized void setPriority(CommandPrompter plugin, EventPriority newPriority) {
            var logger = plugin.getPluginLogger();
            logger.debug("Setting PromptResponseListener priority from '{0}' to '{1}'",
                    getCurrentEventPriority(plugin).name(), newPriority.name());
            var handlerList = AsyncChatEvent.getHandlerList();
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

            logger.info(mm("PromptResponsePriority is now '{0}'", getCurrentEventPriority(plugin).name()));
            listAllRegisteredListeners(plugin);
        }

        private static EventPriority getCurrentEventPriority(CommandPrompter plugin) {
            for (RegisteredListener registeredListener : AsyncChatEvent.getHandlerList()
                    .getRegisteredListeners()) {
                if (registeredListener.getPlugin().getName().equals(plugin.getName()))
                    return registeredListener.getPriority();
            }
            return null;
        }

        private static void listAllRegisteredListeners(CommandPrompter plugin) {
            var logger = plugin.getPluginLogger();
            logger.debug("Registered Listeners: ");
            for (RegisteredListener registeredListener : AsyncChatEvent.getHandlerList()
                    .getRegisteredListeners()) {
                logger.debug("  - '{0}'", registeredListener.getListener().getClass().getSimpleName());
                logger.debug("      Priority: " + registeredListener.getPriority());
                logger.debug("      Plugin: " + registeredListener.getPlugin().getName());
            }
        }

    }
}
