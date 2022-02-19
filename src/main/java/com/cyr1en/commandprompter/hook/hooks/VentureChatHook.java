package com.cyr1en.commandprompter.hook.hooks;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.hook.annotations.TargetPlugin;
import mineverse.Aust1n46.chat.channel.ChatChannel;

@TargetPlugin(pluginName = "VentureChat")
public class VentureChatHook {

    private final CommandPrompter p;

    private VentureChatHook(CommandPrompter plugin) {
        this.p = plugin;
    }

    public boolean isChatChannel(String alias) {
        return ChatChannel.isChannel(alias);
    }

}
