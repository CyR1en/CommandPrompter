package com.cyr1en.commandprompter.prompt;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.PluginLogger;
import com.cyr1en.commandprompter.api.Dispatcher;
import com.cyr1en.commandprompter.api.prompt.Prompt;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Objects;

public class PromptQueue extends LinkedList<Prompt> {

    private String command;
    private final LinkedList<String> completed;
    private final String escapedRegex;
    private final boolean isOp;

    private final boolean isSetPermissionAttachment;

    private PostCommandMeta postCommandMeta;

    private final PluginLogger logger;

    public PromptQueue(String command, boolean isOp, boolean isSetPermissionAttachment, String escapedRegex) {
        super();
        this.command = command;
        this.escapedRegex = escapedRegex;
        this.completed = new LinkedList<>();
        this.isOp = isOp;
        this.isSetPermissionAttachment = isSetPermissionAttachment;
        logger = CommandPrompter.getInstance().getPluginLogger();
    }

    public void addCompleted(String s) {
        completed.add(s);
    }

    public boolean isOp() {
        return isOp;
    }

    public boolean isSetPermissionAttachment() {
        return isSetPermissionAttachment;
    }

    public String getCompleteCommand() {
        command = command.formatted(completed);
        LinkedList<String> completedClone = new LinkedList<>(this.completed);
        while (!completedClone.isEmpty())
            command = command.replaceFirst(escapedRegex, completedClone.pollFirst());
        return "/" + command;
    }

    public void setPCM(PostCommandMeta pcm) {
        this.postCommandMeta = pcm;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public void dispatch(CommandPrompter plugin, Player sender) {
        if (isSetPermissionAttachment())
            Dispatcher.dispatchWithAttachment(plugin, (Player) sender, getCompleteCommand(),
                    plugin.getConfiguration().permissionAttachmentTicks(),
                    plugin.getConfiguration().attachmentPermissions().toArray(new String[0]));
        else
            Dispatcher.dispatchCommand(plugin, (Player) sender, getCompleteCommand());

        if (Objects.nonNull(postCommandMeta))
            execPCM((Player) sender);
    }

    private void execPCM(Player sender) {
        logger.debug("Executing PCM: " + postCommandMeta.command());

        var command = postCommandMeta.command();
        var promptIndex = postCommandMeta.promptIndex();

        logger.debug("Completed: " + completed);
        logger.debug("Replacing placeholders...");
        for (int i = 0; i < promptIndex.length; i++) {
            var index = promptIndex[i];
            if (index >= completed.size()) {
                CommandPrompter.getInstance().getMessenger().sendMessage(sender, "&6" + index + "&c is out of bounds!");
                sender.sendMessage();
                return;
            }
            command = command.replaceFirst("p:" + i, completed.get(index));
        }
        logger.debug("Dispatching Post Command: " + command);
        Dispatcher.dispatchCommand(CommandPrompter.getInstance(), sender, command);
    }

    /**
     * @param promptIndex This will hold the index of the prompt answers to be injected in this post command.
     */
    public record PostCommandMeta(String command, int[] promptIndex) {
        @Override
        public String toString() {
            return "PostCommandMeta{" +
                    "command='" + command + '\'' +
                    ", promptIndex=" + Arrays.toString(promptIndex) +
                    '}';
        }
    }

}
