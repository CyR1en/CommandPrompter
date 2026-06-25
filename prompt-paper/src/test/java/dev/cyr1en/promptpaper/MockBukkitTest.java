package dev.cyr1en.promptpaper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import dev.cyr1en.promptcore.i18n.Placeholder;
import dev.cyr1en.promptpaper.config.CommandPrompterConfig;
import dev.cyr1en.promptpaper.config.PaperConfigLoader;
import dev.cyr1en.promptpaper.config.PromptConfig;
import dev.cyr1en.promptpaper.config.sub.DialogConfig;
import dev.cyr1en.promptpaper.hook.HookContainer;
import dev.cyr1en.promptpaper.i18n.PaperI18n;
import dev.cyr1en.promptpaper.testutil.MockScheduler;
import dev.cyr1en.promptpaper.util.PluginLogger;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

public class MockBukkitTest {

    protected ServerMock server;
    protected CommandPrompter plugin;
    protected MockScheduler scheduler;
    protected PluginLogger pluginLogger;
    protected PaperConfigLoader configLoader;
    protected CommandPrompterConfig config;
    protected PromptConfig promptConfig;
    protected PaperI18n i18n;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();

        plugin = mock(CommandPrompter.class);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getName()).thenReturn("CommandPrompterPaper");
        when(plugin.getLogger()).thenReturn(Logger.getLogger("CommandPrompterPaper"));
        when(plugin.getComponentLogger()).thenReturn(ComponentLogger.logger("CommandPrompterPaper"));
        when(plugin.isEnabled()).thenReturn(true);

        config = mock(CommandPrompterConfig.class);
        when(config.fancyLogger()).thenReturn(false);
        when(config.debugMode()).thenReturn(false);
        when(config.promptPrefix()).thenReturn("[Prompter] ");
        when(config.promptTimeout()).thenReturn(300);
        when(config.cancelKeyword()).thenReturn("cancel");
        when(config.ignoredCommands()).thenReturn(java.util.List.of());
        when(config.locale()).thenReturn("en_US");

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

        i18n = mock(PaperI18n.class);

        // Default: any key without context returns empty component
        when(i18n.get(anyString())).thenReturn(Component.empty());
        when(i18n.get(anyString(), any(Placeholder[].class))).thenReturn(Component.empty());

        // Key-specific stubs (no-context overload)
        when(i18n.get("prompt.cancelled")).thenReturn(Component.text("Prompt cancelled."));
        when(i18n.get("prompt.timed_out")).thenReturn(Component.text("Prompt timed out."));
        when(i18n.get("validation.invalid_integer")).thenReturn(Component.text("Please enter a valid integer."));
        when(i18n.get("validation.invalid_string")).thenReturn(Component.text("Input cannot be empty."));
        when(i18n.get("prompt.error.invalid_title_filter")).thenReturn(
                Component.text("Invalid prompt configuration: TITLE filter cannot be used on a non-compound tag."));
        when(i18n.get("command.error.players_only")).thenReturn(Component.text("Only players can cancel prompts."));
        when(i18n.get("command.cancel.no_active_prompt")).thenReturn(Component.text("You have no active prompt."));
        when(i18n.get("command.reload.success")).thenReturn(Component.text("Configuration reloaded."));
        when(i18n.get("player_ui.search_instruction")).thenReturn(Component.text("Type your search term in chat."));
        when(i18n.get("dialog.no_options")).thenReturn(Component.text("No options available, enter argument manually."));

        // Key-specific stubs with placeholder (no-context overload, varargs)
        when(i18n.get(eq("prompt.error.command_failed"), any(Placeholder[].class)))
                .thenReturn(Component.text("Command failed."));
        when(i18n.get(eq("command.delegate.unknown_permission"), any(Placeholder[].class)))
                .thenReturn(Component.text("Unknown permission key."));
        when(i18n.get(eq("command.reload.failed"), any(Placeholder[].class)))
                .thenReturn(Component.text("Failed to reload."));
        when(i18n.get(eq("command.version"), any(Placeholder[].class)))
                .thenReturn(Component.text("CommandPrompterPaper v3.0.1-test"));
        when(i18n.get(eq("dialog.too_many_options"), any(Placeholder[].class)))
                .thenReturn(Component.text("Too many options, enter argument manually."));

        // Key-specific stubs with player context
        when(i18n.get(eq("prompt.error.command_failed"), any(Player.class), any(Placeholder[].class)))
                .thenReturn(Component.text("Command failed."));
        when(i18n.get(eq("command.delegate.unknown_permission"), any(Player.class), any(Placeholder[].class)))
                .thenReturn(Component.text("Unknown permission key."));
        when(i18n.get(eq("command.delegate.unknown_permission"), isNull(), any(Placeholder[].class)))
                .thenReturn(Component.text("Unknown permission key."));
        when(i18n.get(eq("command.version"), any(Player.class), any(Placeholder[].class)))
                .thenReturn(Component.text("CommandPrompterPaper v3.0.1-test"));
        when(i18n.get(eq("command.version"), isNull(), any(Placeholder[].class)))
                .thenReturn(Component.text("CommandPrompterPaper v3.0.1-test"));
        when(i18n.get(eq("dialog.no_options"), any(Player.class)))
                .thenReturn(Component.text("No options available, enter argument manually."));
        when(i18n.get(eq("dialog.too_many_options"), any(Player.class), any(Placeholder[].class)))
                .thenReturn(Component.text("Too many options, enter argument manually."));

        when(configLoader.getI18n()).thenReturn(i18n);

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
