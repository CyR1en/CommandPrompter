package us.cyrien.cp.listener;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.scheduler.BukkitRunnable;
import us.cyrien.cp.CommandPrompter;

import java.util.List;

public class Prompt implements Listener {

    private CommandPrompter plugin;
    private Player sender;
    private List<String> prompts;
    private String message;

    public Prompt(CommandPrompter plugin, Player sender, List<String> prompts, String message) {
        this.plugin = plugin;
        this.sender = sender;
        this.prompts = prompts;
        this.message = message;
        sendPrompt();
    }

    private void sendPrompt() {
        String prompt = prompts.get(0).replaceAll("<", "");
        prompt = prompt.replaceAll(">", "");
        String prefix = plugin.getConfiguration().getString("Prompt-Prefix");
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + " " + prompt));
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if(!event.getPlayer().equals(sender))
            return;
        if(prompts.size() > 1) {
            prompts.set(0, prompts.get(0).replaceAll("[^\\w\\s<>]", "\\\\$0"));
            message = message.replaceAll(prompts.get(0), event.getMessage());
            prompts.remove(0);
            sendPrompt();
            event.setCancelled(true);
        } else if (prompts.size() == 1){
            prompts.set(0, prompts.get(0).replaceAll("[^\\w\\s<>]", "\\\\$0"));
            message = message.replaceAll(prompts.get(0), event.getMessage());
            prompts.remove(0);
            dispatch(sender, message);
            event.setCancelled(true);
        }
    }

    private void dispatch(Player sender, String command) {
        new BukkitRunnable() {
            public void run() {
                sender.chat(command);
            }
        }.runTask(plugin);
        HandlerList.unregisterAll(this);
    }

}
