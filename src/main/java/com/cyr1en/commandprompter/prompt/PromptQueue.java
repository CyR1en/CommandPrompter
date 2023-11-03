package com.cyr1en.commandprompter.prompt;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.PluginLogger;
import com.cyr1en.commandprompter.api.Dispatcher;
import com.cyr1en.commandprompter.api.prompt.Prompt;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

public class PromptQueue extends LinkedList<Prompt> {

    private String command;
    private final LinkedList<String> completed;
    private final String escapedRegex;

    private final boolean isOp;
    private final boolean isDelegate;
    private final boolean isSetPermissionAttachment;

    private final List<PostCommandMeta> postCommandMetas;

    private final PluginLogger logger;

    public PromptQueue(String command, boolean isOp, boolean isSetPermissionAttachment, boolean isDelegate, String escapedRegex) {
        super();
        this.command = command;
        this.escapedRegex = escapedRegex;
        this.completed = new LinkedList<>();
        this.isOp = isOp;
        this.isDelegate = isDelegate;
        this.isSetPermissionAttachment = isSetPermissionAttachment;
        this.postCommandMetas = new LinkedList<>();
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

    public boolean isDelegate() {
        return isDelegate;
    }

    public String getCompleteCommand() {
        command = command.formatted(completed);
        LinkedList<String> completedClone = new LinkedList<>(this.completed);
        while (!completedClone.isEmpty())
            command = command.replaceFirst(escapedRegex, completedClone.pollFirst());
        return "/" + command;
    }

    public void addPCM(PostCommandMeta pcm) {
        postCommandMetas.add(pcm);
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
        else if (isDelegate()) {
            logger.debug("Dispatching as console");
            Dispatcher.dispatchConsole(getCompleteCommand());
        }
        else
            Dispatcher.dispatchCommand(plugin, (Player) sender, getCompleteCommand());

        if (!postCommandMetas.isEmpty())
            postCommandMetas.forEach(pcm -> execPCM(pcm, sender));
    }

    private void execPCM(PostCommandMeta postCommandMeta, Player sender) {
        logger.debug("Executing PCM: " + postCommandMeta.command());

        var completedClone = new LinkedList<>(completed);
        var i18N = CommandPrompter.getInstance().getI18N();
        var command = postCommandMeta.makeAsCommand(completedClone, index -> {
            var message = i18N.getFormattedProperty("PCMOutOfBounds", index);
            CommandPrompter.getInstance().getMessenger().sendMessage(sender, message);
        });
        logger.debug("After parse: " + command);
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

        public String makeAsCommand(LinkedList<String> promptAnswers) {
            return makeAsCommand(promptAnswers, index -> {
            });
        }

        public String makeAsCommand(LinkedList<String> promptAnswers, Consumer<String> onOutOfBounds) {
            if (promptAnswers == null || promptAnswers.isEmpty())
                return command;
            var command = this.command;
            var promptIndex = this.promptIndex;
            for (int index : promptIndex) {
                if (index >= promptAnswers.size() || index < 0) {
                    onOutOfBounds.accept(String.valueOf(index));
                    continue;
                }
                command = command.replaceFirst("p:" + index, promptAnswers.get(index));
            }
            return command;
        }
    }

}
