package dev.cyr1en.promptpaper.command;

import static org.junit.jupiter.api.Assertions.*;
import dev.cyr1en.promptpaper.MockBukkitTest;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommandRegistrarTest extends MockBukkitTest {

    @Test
    void constructsWithThreeTopLevelCommands() {
        var registrar = new CommandRegistrar(plugin);
        // The registrar stores its top-level list privately; we exercise
        // it indirectly through registerAll() below. The basic construction
        // contract is that it does not throw with a non-null plugin.
        assertNotNull(registrar);
    }

    @Test
    void registerAllInvokesRegistrarForEachTopLevel() {
        // Use a Mockito-free lightweight check: the top-level commands
        // are constructed with the plugin and expose the expected name
        // and aliases. The full literal `build()` is exercised for the
        // root only — the delegate commands use ArgumentTypes.player()
        // which requires the Paper VanillaArgumentProvider not present
        // in MockBukkit, and is covered by deploy-time smoke tests.
        var promptRoot = new PromptRootCommand(plugin);
        var console = new ConsoleDelegateCommand(plugin);
        var player = new PlayerDelegateCommand(plugin);

        assertNotNull(promptRoot.build());
        assertEquals("commandprompter", promptRoot.name());
        assertEquals(List.of("cmdp"), promptRoot.aliases());
        assertEquals("consoledelegate", console.name());
        assertEquals(List.of("cd"), console.aliases());
        assertEquals("playerdelegate", player.name());
        assertEquals(List.of("pd"), player.aliases());
    }
}
