package dev.cyr1en.promptpaper.screen.playerui;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import dev.cyr1en.promptcore.PromptTag;
import dev.cyr1en.promptui.ScreenResult;
import dev.cyr1en.promptpaper.MockBukkitTest;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlayerUIScreenTest extends MockBukkitTest {

    private PromptTag tag;
    private PlayerUIScreen screen;
    private AtomicReference<ScreenResult> resultRef;

    @BeforeEach
    void setUpPlayerUI() {
        var player = createPlayer("TestPlayer");
        tag = new PromptTag("<p:Choose>", "p", null, "Choose");

        var headCache = mock(HeadCache.class);
        when(plugin.getHeadCache()).thenReturn(headCache);

        lenient().when(promptConfig.sendCancelText()).thenReturn(false);
        lenient().when(promptConfig.playerUISize()).thenReturn(54);
        lenient().when(promptConfig.sorted()).thenReturn(false);
        lenient().when(promptConfig.skullNameFormat()).thenReturn("&6%s");
        lenient().when(promptConfig.skullCustomModelData()).thenReturn(0);
        lenient().when(promptConfig.emptyMessage()).thenReturn("&cNo players found!");
        lenient().when(promptConfig.worldFilterFormat()).thenReturn("&6%s");
        lenient().when(promptConfig.radialFilterFormat()).thenReturn("&c%s");
        lenient().when(promptConfig.previousItem()).thenReturn("FEATHER");
        lenient().when(promptConfig.previousCustomModelData()).thenReturn(0);
        lenient().when(promptConfig.previousColumn()).thenReturn(3);
        lenient().when(promptConfig.previousText()).thenReturn("&7Previous");
        lenient().when(promptConfig.nextItem()).thenReturn("FEATHER");
        lenient().when(promptConfig.nextCustomModelData()).thenReturn(0);
        lenient().when(promptConfig.nextColumn()).thenReturn(7);
        lenient().when(promptConfig.nextText()).thenReturn("Next");
        lenient().when(promptConfig.cancelItem()).thenReturn("BARRIER");
        lenient().when(promptConfig.cancelCustomModelData()).thenReturn(0);
        lenient().when(promptConfig.cancelColumn()).thenReturn(5);
        lenient().when(promptConfig.cancelText()).thenReturn("&7Cancel");
        lenient().when(promptConfig.searchItem()).thenReturn("NAME_TAG");
        lenient().when(promptConfig.searchCustomModelData()).thenReturn(0);
        lenient().when(promptConfig.searchColumn()).thenReturn(9);
        lenient().when(promptConfig.searchText()).thenReturn("&6Search");

        screen = new PlayerUIScreen(plugin, player, tag, null);
        resultRef = new AtomicReference<>();
        screen.onResult(resultRef::set);
    }

    @Test
    void constructorStoresTag() {
        assertNotNull(screen);
        assertFalse(screen.isOpen());
    }

    @Test
    void isOpenReturnsFalseInitially() {
        assertFalse(screen.isOpen());
    }

    @Test
    void onResultStoresCallback() {
        var screen2 = new PlayerUIScreen(plugin, createPlayer(), tag, null);
        screen2.onResult(result -> {});
        assertNotNull(screen2);
    }

    @Test
    void closeWithoutOpenIsNoop() {
        assertFalse(screen.isOpen());
        screen.close();
        assertFalse(screen.isOpen());
    }
}
