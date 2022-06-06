package com.cyr1en.commandprompter.prompt;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.unsafe.PvtFieldMutator;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.RegisteredListener;
import org.fusesource.jansi.Ansi;

import java.util.*;

@SuppressWarnings("unused")
public class PromptResponseListener implements Listener {

    private final PromptManager manager;
    private final CommandPrompter plugin;

    public PromptResponseListener(PromptManager manager, CommandPrompter plugin) {
        this.manager = manager;
        this.plugin = plugin;
    }

    public PromptManager getManager() {
        return this.manager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!manager.getPromptRegistry().inCommandProcess(event.getPlayer()))
            return;
        event.setCancelled(true);
        var message = ChatColor.stripColor(
                ChatColor.translateAlternateColorCodes('&', event.getMessage()));
        var cancelKeyword = plugin.getConfiguration().cancelKeyword();

        if (cancelKeyword.equalsIgnoreCase(message))
            manager.cancel(event.getPlayer());
        var ctx = new PromptContext(event, event.getPlayer(), message);
        Bukkit.getScheduler().runTask(plugin, () -> manager.processPrompt(ctx));
    }

    public static void setPriority(CommandPrompter plugin) {
        var configPriority = plugin.getPromptConfig().responseListenerPriority().toUpperCase(Locale.ROOT);
        if (configPriority.equals("DEFAULT")) return;

        listAllRegisteredListeners(plugin);
        var logger = plugin.getPluginLogger();
        var currentPriority = getCurrentEventPriority(plugin);
        if (Objects.isNull(currentPriority)) return;

        var priority = EventPriority.LOWEST;
        try {
            priority = EventPriority.valueOf(configPriority);
        } catch (IllegalArgumentException ignore) {
            logger.err("Could not set '%s' as priority for PromptResponseListener. Defaulted to '%s'",
                    configPriority, priority.name());
        }
        // Do nothing if current priority = config priority
        if (currentPriority.name().equals(priority.name())) return;

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
            var handlerSlots = (EnumMap<EventPriority, ArrayList<RegisteredListener>>) handlerSlotsF.get(handlerList);
            var currentPriority = getCurrentEventPriority(plugin);

            var registeredListener = handlerSlots.get(currentPriority).stream()
                    .filter(rL -> rL.getListener().getClass().equals(PromptResponseListener.class))
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
        for (RegisteredListener registeredListener : AsyncPlayerChatEvent.getHandlerList().getRegisteredListeners()) {
            if (registeredListener.getPlugin().getName().equals(plugin.getName()))
                return registeredListener.getPriority();
        }
        return null;
    }

    private static void listAllRegisteredListeners(CommandPrompter plugin) {
        var logger = plugin.getPluginLogger();
        logger.debug("Registered Listeners: ");
        for (RegisteredListener registeredListener : AsyncPlayerChatEvent.getHandlerList().getRegisteredListeners()) {
            logger.debug("  - '%s'", registeredListener.getListener().getClass().getSimpleName());
            logger.debug("      Priority: " + registeredListener.getPriority());
            logger.debug("      Plugin: " + registeredListener.getPlugin().getName());
        }
    }

}
