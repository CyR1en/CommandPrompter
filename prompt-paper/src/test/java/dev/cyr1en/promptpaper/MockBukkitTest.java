package dev.cyr1en.promptpaper;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import dev.cyr1en.promptpaper.config.CommandPrompterConfig;
import dev.cyr1en.promptpaper.config.PaperConfigLoader;
import dev.cyr1en.promptpaper.config.PromptConfig;
import dev.cyr1en.promptpaper.config.sub.DialogConfig;
import dev.cyr1en.promptpaper.hook.HookContainer;
import dev.cyr1en.promptpaper.testutil.MockScheduler;
import dev.cyr1en.promptpaper.util.PluginLogger;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

public class MockBukkitTest {

    protected ServerMock server;
    protected CommandPrompter plugin;
    protected MockScheduler scheduler;
    protected PluginLogger pluginLogger;
    protected PaperConfigLoader configLoader;
    protected CommandPrompterConfig config;
    protected PromptConfig promptConfig;
    protected dev.cyr1en.promptpaper.config.MessageConfig messageConfig;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();

        plugin = mock(CommandPrompter.class);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getName()).thenReturn("CommandPrompterPaper");
        when(plugin.getLogger()).thenReturn(Logger.getLogger("CommandPrompterPaper"));
        when(plugin.isEnabled()).thenReturn(true);

        config = mock(CommandPrompterConfig.class);
        when(config.fancyLogger()).thenReturn(false);
        when(config.debugMode()).thenReturn(false);
        when(config.promptPrefix()).thenReturn("[Prompter] ");
        when(config.promptTimeout()).thenReturn(300);
        when(config.cancelKeyword()).thenReturn("cancel");
        when(config.ignoredCommands()).thenReturn(java.util.List.of());

        configLoader = mock(PaperConfigLoader.class);
        when(configLoader.getConfig()).thenReturn(config);

        promptConfig = mock(PromptConfig.class);
        when(promptConfig.dialogConfig()).thenReturn(DialogConfig.legacy(
                "Prompt",
                "<green>Confirm</green>",
                "Confirm this action",
                "<red>Cancel</red>",
                "Cancel this action"));

        when(configLoader.getPromptConfig()).thenReturn(promptConfig);
        when(plugin.getConfigLoader()).thenReturn(configLoader);

        messageConfig = mock(dev.cyr1en.promptpaper.config.MessageConfig.class);
        when(messageConfig.promptCancelled()).thenReturn("<yellow>Prompt cancelled.</yellow>");
        when(messageConfig.promptTimedOut()).thenReturn("<red>Prompt timed out.</red>");
        when(messageConfig.invalidInteger()).thenReturn("<red>Please enter a valid integer.</red>");
        when(messageConfig.invalidString()).thenReturn("<red>Input cannot be empty.</red>");
        when(configLoader.getMessageConfig()).thenReturn(messageConfig);

        pluginLogger = new PluginLogger(plugin);
        when(plugin.getPluginLogger()).thenReturn(pluginLogger);

        var hookContainer = new HookContainer(plugin);
        when(plugin.getHookContainer()).thenReturn(hookContainer);

        scheduler = new MockScheduler(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    protected PlayerMock createPlayer() {
        return server.addPlayer();
    }

    protected PlayerMock createPlayer(String name) {
        return server.addPlayer(name);
    }

    protected void performTicks(long ticks) {
        server.getScheduler().performTicks(ticks);
    }

    protected void performOneTick() {
        server.getScheduler().performOneTick();
    }
}
