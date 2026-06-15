package dev.cyr1en.promptpaper.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import dev.cyr1en.promptcore.PromptTag;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.screen.AnvilPromptScreen;
import dev.cyr1en.promptpaper.screen.ChatPromptScreen;
import dev.cyr1en.promptpaper.screen.playerui.PlayerUIScreen;
import dev.cyr1en.promptpaper.screen.ScreenRouter;
import dev.cyr1en.promptpaper.screen.SignPromptScreen;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class ScreenRouterTest extends MockBukkitTest {

    private ScreenRouter router;
    private PromptTag chatTag;
    private PromptTag anvilTag;
    private PromptTag signTag;
    private PromptTag dialogTag;
    private PromptTag playerTag;

    @BeforeEach
    void setUpRouter() {
        var promptConfig = mock(PromptConfig.class);
        when(promptConfig.getScreenMappings()).thenReturn(Map.of(
                "", ScreenType.CHAT,
                "a", ScreenType.ANVIL,
                "s", ScreenType.SIGN,
                "d", ScreenType.DIALOG,
                "p", ScreenType.PLAYER
        ));
        when(configLoader.getPromptConfig()).thenReturn(promptConfig);
        lenient().when(promptConfig.sendCancelText()).thenReturn(false);

        router = new ScreenRouter(plugin);
        chatTag = new PromptTag("<test>", "", null, "Enter value");
        anvilTag = new PromptTag("<a:Enter value>", "a", null, "Enter value");
        signTag = new PromptTag("<s:Enter value>", "s", null, "Enter value");
        dialogTag = new PromptTag("<d:Confirm?>", "d", null, "Confirm?");
        playerTag = new PromptTag("<p:Choose>", "p", null, "Choose");
    }

    @Test
    void createsChatScreenForChatMapping() {
        var screen = router.create(createPlayer(), chatTag);
        assertInstanceOf(ChatPromptScreen.class, screen);
    }

    @Test
    void unknownKeyFallsBackToChat() {
        var tag = new PromptTag("<x:text>", "x", null, "text");
        var screen = router.create(createPlayer(), tag);
        assertInstanceOf(ChatPromptScreen.class, screen);
    }

    @Test
    void createsAnvilScreenForAnvilMapping() {
        var player = createPlayer();
        var screen = router.create(player, anvilTag);
        assertInstanceOf(AnvilPromptScreen.class, screen);
    }

    @Test
    void createsSignScreenForSignMapping() {
        var player = createPlayer();
        var screen = router.create(player, signTag);
        assertInstanceOf(SignPromptScreen.class, screen);
    }

    @Test
    @Disabled("DialogPromptScreen requires the Paper Dialog API on the test classpath; MockBukkit 4.0.0 does not include it. See DialogInputKindTest / DialogConstraintsTest for unit coverage of the new filter syntax, and the manual smoke test in the dialog plan for end-to-end coverage.")
    void createsDialogScreenForDialogMapping() {
    }

    @Test
    @Disabled("Same reason as createsDialogScreenForDialogMapping. DialogInputKindTest covers the kind parsing.")
    void createsDialogScreenForBoolFilter() {
    }

    @Test
    @Disabled("Same reason as createsDialogScreenForDialogMapping. DialogConstraintsTest covers the num[a,b,c,d] parsing.")
    void createsDialogScreenForNumFilter() {
    }

    @Test
    void createsPlayerScreenForPlayerMapping() {
        var player = createPlayer();
        var screen = router.create(player, playerTag);
        assertInstanceOf(PlayerUIScreen.class, screen);
    }
}
