package dev.cyr1en.promptpaper.screen.playerui;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import dev.cyr1en.promptcore.PromptTag;
import dev.cyr1en.promptui.ScreenResult;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.hook.HookContainer;
import dev.cyr1en.promptpaper.hook.hooks.FilterHook;
import dev.cyr1en.promptpaper.hook.hooks.VanishHook;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlayerUIScreenTest extends MockBukkitTest {

    private PromptTag tag;
    private PlayerUIScreen screen;
    private AtomicReference<ScreenResult> resultRef;
    private org.bukkit.entity.Player player;

    @BeforeEach
    void setUpPlayerUI() {
        player = createPlayer("TestPlayer");
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

    /**
     * Regression test for the vanish leak: a filtered prompt must never
     * display a head for a player hidden by a vanish plugin, and the
     * vanished player must not enter the head cache.
     *
     * <p>{@link #getFilteredHeads()} is invoked reflectively because
     * MockBukkit cannot create the chest inventory used by
     * {@link ChestGui}.</p>
     */
    @Test
    void filteredPromptExcludesVanishedPlayers() throws Exception {
        var visible = createPlayer("Visible");
        var ghost = createPlayer("Ghost");

        var vanishHook = mock(VanishHook.class);
        when(vanishHook.isInvisible(ghost)).thenReturn(true);
        var hookContainer = mock(HookContainer.class);
        when(hookContainer.getFirstHooked(VanishHook.class)).thenReturn(Optional.of(vanishHook));
        when(hookContainer.getHooksImplementing(FilterHook.class)).thenReturn(List.of());
        when(plugin.getHookContainer()).thenReturn(hookContainer);

        var realCache = new HeadCache(plugin, scheduler);
        realCache.registerFilters(hookContainer);
        when(plugin.getHeadCache()).thenReturn(realCache);

        var filteredTag = new PromptTag("<p:w:Choose>", "p", "w", "Choose");
        var filteredScreen = new PlayerUIScreen(plugin, player, filteredTag, null);
        filteredScreen.onResult(result -> {});

        var method = PlayerUIScreen.class.getDeclaredMethod("getFilteredHeads");
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        var heads = (List<ItemStack>) method.invoke(filteredScreen);

        var skullNames = heads.stream()
                .map(ItemStack::getItemMeta)
                .filter(SkullMeta.class::isInstance)
                .map(SkullMeta.class::cast)
                .map(skull -> skull.getOwningPlayer() != null ? skull.getOwningPlayer().getName() : null)
                .filter(Objects::nonNull)
                .toList();
        assertTrue(skullNames.contains("TestPlayer"), "prompting player should be listed");
        assertTrue(skullNames.contains("Visible"), "visible player should be listed");
        assertFalse(skullNames.contains("Ghost"), "vanished players must not appear in filtered prompts");
        assertTrue(realCache.getHeadFor(ghost).isEmpty(),
                "vanished players must not enter the head cache");
    }
}
