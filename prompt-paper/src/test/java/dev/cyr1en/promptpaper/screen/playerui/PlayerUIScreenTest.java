package dev.cyr1en.promptpaper.screen.playerui;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import dev.cyr1en.promptcore.PromptTag;
import dev.cyr1en.promptui.AnvilInputScreen;
import dev.cyr1en.promptui.ScreenProvider;
import dev.cyr1en.promptui.ScreenResult;
import dev.cyr1en.promptui.gui.GuiItem;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.config.PromptConfig;
import dev.cyr1en.promptpaper.hook.HookContainer;
import dev.cyr1en.promptpaper.hook.hooks.FilterHook;
import dev.cyr1en.promptpaper.hook.hooks.VanishHook;
import dev.cyr1en.promptpaper.preset.PlayerUiPrompt;
import dev.cyr1en.promptpaper.preset.UIButton;
import dev.cyr1en.promptui.pane.StaticPane;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
        lenient().when(promptConfig.getFilterFormat("World")).thenReturn("&6%s");
        lenient().when(promptConfig.getFilterFormat("Radial")).thenReturn("&c%s");
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
        lenient().when(promptConfig.searchAnvilItemTitle()).thenReturn("&6&lPlayer Search");
        lenient().when(promptConfig.searchAnvilItem()).thenReturn("NAME_TAG");
        lenient().when(promptConfig.searchAnvilItemCustomModelData()).thenReturn(42);
        lenient().when(promptConfig.searchAnvilItemText()).thenReturn("&7Type here");
        lenient().when(promptConfig.cancelItem()).thenReturn("BARRIER");

        screen = new PlayerUIScreen(plugin, player, tag, null, List.of());
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
        var screen2 = new PlayerUIScreen(plugin, createPlayer(), tag, null, List.of());
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
        var filteredScreen = new PlayerUIScreen(plugin, player, filteredTag, null, List.of());
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

    /**
     * Regression for #85: the display list is decoupled from the head cache,
     * so an unpopulated cache must never hide online players.
     */
    @Test
    void unfilteredShowsPlayersRegardlessOfCacheState() throws Exception {
        var realCache = stubRealCacheWithBuiltins();
        createPlayer("Alpha");
        createPlayer("Beta");
        // Intentionally leave the cache unpopulated.
        assertEquals(0, realCache.size());

        var names = skullNames(screen);

        assertTrue(names.contains("TestPlayer"), "prompting player must be shown from Bukkit");
        assertTrue(names.contains("Alpha"), "online player must be shown despite empty cache");
        assertTrue(names.contains("Beta"), "online player must be shown despite empty cache");
    }

    /**
     * Regression for #86: the empty-state item must use the configured
     * {@code PlayerUI.Empty-Message} on a BARRIER with no click action.
     */
    @Test
    void buildEmptyStateItemUsesConfiguredMessage() throws Exception {
        var method = PlayerUIScreen.class.getDeclaredMethod("buildEmptyStateItem", PromptConfig.class);
        method.setAccessible(true);
        var guiItem = (GuiItem) method.invoke(screen, promptConfig);

        assertEquals(Material.BARRIER, guiItem.getItem().getType());
        assertFalse(guiItem.hasAction(), "the empty-state item must not be clickable");
        var meta = guiItem.getItem().getItemMeta();
        assertNotNull(meta);
        Component display = meta.displayName();
        assertNotNull(display, "the empty-state item must carry the configured message");
        Style coloredStyle = findStyleWithColor(display);
        assertNotNull(coloredStyle, "expected a style with a color in the component tree");
        assertEquals(NamedTextColor.RED, coloredStyle.color(),
                "&c in the configured empty message should render as RED, not literal text");
    }

    /**
     * Regression for #86: a filtered prompt with no matching players yields an
     * empty head list (which the screen renders as the empty-state item).
     */
    @Test
    void filteredHeadsEmptyWhenNoPlayers() throws Exception {
        stubRealCacheWithBuiltins();
        // The only online player is the prompting player, whom a radial filter
        // includes by definition (distance 0); disconnect him so the server has
        // no players matching r10 and the filtered list is empty.
        ((org.mockbukkit.mockbukkit.entity.PlayerMock) player).disconnect();

        var names = skullNames(buildFilteredScreen("r10"));

        assertTrue(names.isEmpty(), "no matching players must yield an empty head list");
    }

    /**
     * Regression for #88: starting a search must prefer an anvil provider and
     * configure it with the {@code PlayerUI.Search.AnvilItem.*} values; the
     * anvil answer then filters the currently displayed heads.
     */
    @Test
    void searchUsesAnvilWithConfiguredValues() throws Exception {
        stubRealCacheWithBuiltins();
        createPlayer("Nearby");
        createPlayer("Other");

        var anvilScreen = mock(AnvilInputScreen.class);
        var provider = mock(ScreenProvider.class);
        when(provider.createAnvil(any(), any(), any())).thenReturn(anvilScreen);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> configCaptor = ArgumentCaptor.forClass(Map.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<ScreenResult>> resultCaptor = ArgumentCaptor.forClass(Consumer.class);

        var searchScreen = new PlayerUIScreen(plugin, player, tag, null, List.of(provider));

        // Populate the private currentHeads from the (fresh-from-Bukkit) head list.
        var getFilteredHeads = PlayerUIScreen.class.getDeclaredMethod("getFilteredHeads");
        getFilteredHeads.setAccessible(true);
        @SuppressWarnings("unchecked")
        var heads = (List<ItemStack>) getFilteredHeads.invoke(searchScreen);
        var currentHeadsField = PlayerUIScreen.class.getDeclaredField("currentHeads");
        currentHeadsField.setAccessible(true);
        currentHeadsField.set(searchScreen, heads);

        var startSearch = PlayerUIScreen.class.getDeclaredMethod("startSearch");
        startSearch.setAccessible(true);
        startSearch.invoke(searchScreen);

        verify(anvilScreen).configure(configCaptor.capture());
        var config = configCaptor.getValue();
        assertNotNull(config, "the anvil screen must be configured");
        assertEquals("&6&lPlayer Search", config.get("customTitle"));
        assertEquals("NAME_TAG", config.get("anvilItem"));
        assertEquals("42", config.get("itemCustomModelData"));
        assertEquals("&7Type here", config.get("promptMessage"));
        assertEquals("true", config.get("enableTitle"));
        assertEquals("true", config.get("enableCancelItem"));
        assertEquals("BARRIER", config.get("anvilCancelItem"));

        verify(anvilScreen).onResult(resultCaptor.capture());
        var onResult = resultCaptor.getValue();
        assertNotNull(onResult, "the anvil screen must receive a result callback");
        onResult.accept(ScreenResult.answer("near"));
        performOneTick();

        @SuppressWarnings("unchecked")
        var currentHeads = (List<ItemStack>) currentHeadsField.get(searchScreen);
        assertEquals(List.of("Nearby"), owningPlayerNames(currentHeads),
                "the anvil search term must filter the displayed heads case-insensitively");
    }

    /**
     * Regression for #88: without any provider the search must fall back to the
     * chat instruction and still filter the displayed heads via chat input.
     */
    @Test
    void searchFallsBackToChatWithoutProviders() throws Exception {
        stubRealCacheWithBuiltins();
        createPlayer("Nearby");
        createPlayer("Other");

        var searchScreen = new PlayerUIScreen(plugin, player, tag, null, List.of());

        var getFilteredHeads = PlayerUIScreen.class.getDeclaredMethod("getFilteredHeads");
        getFilteredHeads.setAccessible(true);
        @SuppressWarnings("unchecked")
        var heads = (List<ItemStack>) getFilteredHeads.invoke(searchScreen);
        var currentHeadsField = PlayerUIScreen.class.getDeclaredField("currentHeads");
        currentHeadsField.setAccessible(true);
        currentHeadsField.set(searchScreen, heads);

        var startSearch = PlayerUIScreen.class.getDeclaredMethod("startSearch");
        startSearch.setAccessible(true);
        startSearch.invoke(searchScreen);

        String instruction = ((org.mockbukkit.mockbukkit.entity.PlayerMock) player).nextMessage();
        assertNotNull(instruction, "chat fallback must send the search instruction");
        assertTrue(instruction.toLowerCase().contains("search"),
                "chat fallback message should mention search, was: " + instruction);
        verify(i18n).get(eq("player_ui.search_instruction"), same(player));

        player.chat("near");
        server.getScheduler().waitAsyncEventsFinished();

        @SuppressWarnings("unchecked")
        var currentHeads = (List<ItemStack>) currentHeadsField.get(searchScreen);
        assertEquals(List.of("Nearby"), owningPlayerNames(currentHeads),
                "the chat search term must filter the displayed heads case-insensitively");
    }

    /**
     * Stubs the plugin's head cache with a real {@link HeadCache} registered
     * with the built-in filters and no integration-hook filters.
     */
    private HeadCache stubRealCacheWithBuiltins() {
        var hookContainer = mock(HookContainer.class);
        when(hookContainer.getFirstHooked(VanishHook.class)).thenReturn(Optional.empty());
        when(hookContainer.getHooksImplementing(FilterHook.class)).thenReturn(List.of());
        when(plugin.getHookContainer()).thenReturn(hookContainer);
        var realCache = new HeadCache(plugin, scheduler);
        realCache.registerFilters(hookContainer);
        when(plugin.getHeadCache()).thenReturn(realCache);
        return realCache;
    }

    /**
     * Builds a screen for the given raw filter string (e.g. {@code r10s})
     * against the currently stubbed head cache.
     */
    private PlayerUIScreen buildFilteredScreen(String filter) {
        var filteredTag = new PromptTag("<p:" + filter + ":Choose>", "p", filter, "Choose");
        var filteredScreen = new PlayerUIScreen(plugin, player, filteredTag, null, List.of());
        filteredScreen.onResult(result -> {});
        return filteredScreen;
    }

    /**
     * Reflectively invokes {@code getFilteredHeads()} and extracts the
     * owning-player names of the resulting skulls.
     */
    private static List<String> skullNames(PlayerUIScreen screen) throws Exception {
        var method = PlayerUIScreen.class.getDeclaredMethod("getFilteredHeads");
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        var heads = (List<ItemStack>) method.invoke(screen);
        return heads.stream()
                .map(ItemStack::getItemMeta)
                .filter(SkullMeta.class::isInstance)
                .map(SkullMeta.class::cast)
                .map(skull -> skull.getOwningPlayer() != null ? skull.getOwningPlayer().getName() : null)
                .filter(Objects::nonNull)
                .toList();
    }

    private void teleportRelative(Player target, double dx, double dz) {
        target.teleport(player.getLocation().clone().add(dx, 0, dz));
    }

    @Test
    void combinedRadialSelfFiltersIntersect() throws Exception {
        stubRealCacheWithBuiltins();
        var nearby = createPlayer("Nearby");
        teleportRelative(nearby, 5, 0);
        var far = createPlayer("Far");
        teleportRelative(far, 100, 0);

        var skullNames = skullNames(buildFilteredScreen("r10s"));

        assertTrue(skullNames.contains("Nearby"), "nearby player must survive the radial∩self intersection");
        assertFalse(skullNames.contains("TestPlayer"), "self filter must exclude the prompting player");
        assertFalse(skullNames.contains("Far"), "far player must be excluded by the radial filter");
    }

    @Test
    void selfRadialOrderDoesNotMatter() throws Exception {
        stubRealCacheWithBuiltins();
        var nearby = createPlayer("Nearby");
        teleportRelative(nearby, 5, 0);
        var far = createPlayer("Far");
        teleportRelative(far, 100, 0);

        var skullNames = skullNames(buildFilteredScreen("sr10"));

        assertTrue(skullNames.contains("Nearby"), "nearby player must survive the self∩radial intersection");
        assertFalse(skullNames.contains("TestPlayer"), "self filter must exclude the prompting player");
        assertFalse(skullNames.contains("Far"), "far player must be excluded by the radial filter");
    }

    @Test
    void worldRadialIntersect() throws Exception {
        stubRealCacheWithBuiltins();
        var nearby = createPlayer("Nearby");
        teleportRelative(nearby, 5, 0);
        var far = createPlayer("Far");
        teleportRelative(far, 100, 0);

        for (var filter : List.of("wr10", "r10w")) {
            var skullNames = skullNames(buildFilteredScreen(filter));
            assertTrue(skullNames.contains("TestPlayer"),
                    filter + ": prompting player must be present (same world, in radius)");
            assertTrue(skullNames.contains("Nearby"),
                    filter + ": nearby player must be present");
            assertFalse(skullNames.contains("Far"),
                    filter + ": far player must be excluded by the radial filter");
        }
    }

    @Test
    void parameterizedFilterCombinesWithBuiltin() throws Exception {
        var hookContainer = mock(HookContainer.class);
        when(hookContainer.getFirstHooked(VanishHook.class)).thenReturn(Optional.empty());
        when(hookContainer.getHooksImplementing(FilterHook.class)).thenReturn(List.of());
        when(plugin.getHookContainer()).thenReturn(hookContainer);
        var realCache = new HeadCache(plugin, scheduler);
        realCache.registerFilter(new NameMatchFilter());
        realCache.registerFilters(hookContainer);
        when(plugin.getHeadCache()).thenReturn(realCache);

        var zed = createPlayer("Zed");
        teleportRelative(zed, 5, 0);
        var other = createPlayer("Other");
        teleportRelative(other, 7, 0);

        var skullNames = skullNames(buildFilteredScreen("r10zzZed;"));

        assertTrue(skullNames.contains("Zed"),
                "parameterized filter must intersect with the radial filter, keeping only Zed");
        assertFalse(skullNames.contains("TestPlayer"), "Zed-only custom filter must drop the prompting player");
        assertFalse(skullNames.contains("Other"), "Zed-only custom filter must drop Other");
        assertEquals(1, skullNames.size(), "exactly one head must survive the intersection");
    }

    /**
     * Regression: for combined keys, the head display format must come from
     * the FIRST (leftmost) filter only — radial in {@code r10s} — while the
     * remaining filters contribute player sets only. The shared cache entry
     * must keep its global format.
     */
    @Test
    void combinedFiltersUseFirstFilterFormat() throws Exception {
        var realCache = stubRealCacheWithBuiltins();
        var nearby = createPlayer("Nearby");
        teleportRelative(nearby, 5, 0);

        var radialFirstColors = skullDisplayColors(buildFilteredScreen("r10s"));
        assertFalse(radialFirstColors.isEmpty());
        assertTrue(radialFirstColors.stream().allMatch(c -> c == NamedTextColor.RED),
                "r10s heads must use the first (radial) filter format");

        var cachedHead = realCache.getHeadFor(nearby);
        assertTrue(cachedHead.isPresent());
        assertEquals(NamedTextColor.GOLD, cachedHead.get().getItemMeta().displayName().color(),
                "applying a filter format must not mutate the shared cache entry");

        var selfFirstColors = skullDisplayColors(buildFilteredScreen("sr10"));
        assertTrue(selfFirstColors.stream().allMatch(c -> c == NamedTextColor.GOLD),
                "sr10 heads must keep the global format when the first filter has no format");
    }

    /**
     * Regression: the same intersection (world ∩ radial) renders with the
     * format of whichever key appears first in the filter string.
     */
    @Test
    void formatFollowsLeftmostFilterKey() throws Exception {
        stubRealCacheWithBuiltins();
        var nearby = createPlayer("Nearby");
        teleportRelative(nearby, 5, 0);

        var worldFirstColors = skullDisplayColors(buildFilteredScreen("wr10"));
        assertTrue(worldFirstColors.stream().allMatch(c -> c == NamedTextColor.GOLD),
                "wr10 must use the world format");

        var radialFirstColors = skullDisplayColors(buildFilteredScreen("r10w"));
        assertTrue(radialFirstColors.stream().allMatch(c -> c == NamedTextColor.RED),
                "r10w must use the radial format");
    }

    /**
     * Reflectively invokes {@code getFilteredHeads()} and extracts the
     * display-name colors of the resulting skulls.
     */
    private static List<TextColor> skullDisplayColors(PlayerUIScreen screen) throws Exception {
        var method = PlayerUIScreen.class.getDeclaredMethod("getFilteredHeads");
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        var heads = (List<ItemStack>) method.invoke(screen);
        return heads.stream()
                .map(ItemStack::getItemMeta)
                .map(meta -> meta != null ? meta.displayName() : null)
                .filter(Objects::nonNull)
                .map(Component::color)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Extracts the owning-player names from a list of head item stacks.
     */
    private static List<String> owningPlayerNames(List<ItemStack> heads) {
        return heads.stream()
                .map(ItemStack::getItemMeta)
                .filter(SkullMeta.class::isInstance)
                .map(SkullMeta.class::cast)
                .map(skull -> skull.getOwningPlayer() != null ? skull.getOwningPlayer().getName() : null)
                .filter(Objects::nonNull)
                .toList();
    }

    private static Style findStyleWithColor(Component root) {
        if (root.style().color() != null) return root.style();
        for (Component child : root.children()) {
            Style found = findStyleWithColor(child);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * Custom filter mimicking a hook's parameterized filter: matches
     * {@code zz<name>;} tokens and returns only online players whose name
     * equals the captured parameter.
     */
    @Test
    void uiButtonConstructorValidatesSlotBounds() {
        assertDoesNotThrow(() -> new UIButton(true, 0, "Text", "BARRIER", "Hover", 0));
        assertDoesNotThrow(() -> new UIButton(true, 8, "Text", "BARRIER", "Hover", 0));
        assertThrows(IllegalArgumentException.class,
                () -> new UIButton(true, -1, "Text", "BARRIER", "Hover", 0));
        assertThrows(IllegalArgumentException.class,
                () -> new UIButton(true, 9, "Text", "BARRIER", "Hover", 0));
        assertThrows(IllegalArgumentException.class,
                () -> new UIButton(true, 53, "Text", "BARRIER", "Hover", 0));
    }

    @Test
    void buildControlPaneRendersHoverTextAsLore() throws Exception {
        var preset = new PlayerUiPrompt(
                "player_ui", "test-pui", "Choose", "online",
                new UIButton(true, 0, "&cCancel", "BARRIER", "&7Click to cancel{br}&8Second line", 0),
                new UIButton(true, 1, "&ePrev", "FEATHER", "&7Go back", 0),
                new UIButton(true, 2, "&aNext", "FEATHER", "&7Go forward", 0),
                true);

        var puiScreen = new PlayerUIScreen(plugin, player, tag, preset, List.of());
        var method = PlayerUIScreen.class.getDeclaredMethod("buildControlPane", PromptConfig.class);
        method.setAccessible(true);
        var pane = (StaticPane) method.invoke(puiScreen, promptConfig);

        var display = pane.display();
        var cancelItem = display.getItem(0, 0);
        assertNotNull(cancelItem);
        var cancelMeta = cancelItem.getItem().getItemMeta();
        assertNotNull(cancelMeta);
        assertNotNull(cancelMeta.lore());
        assertEquals(2, cancelMeta.lore().size());

        var prevItem = display.getItem(1, 0);
        assertNotNull(prevItem);
        var prevMeta = prevItem.getItem().getItemMeta();
        assertNotNull(prevMeta);
        assertNotNull(prevMeta.lore());
        assertEquals(1, prevMeta.lore().size());
    }

    private static class NameMatchFilter extends CacheFilter {
        private final String name;

        NameMatchFilter() { this(""); }

        NameMatchFilter(String name) {
            super(Pattern.compile("zz(\\S+);"), "NameMatch", 1);
            this.name = name;
        }

        @Override public CacheFilter reConstruct(String promptKey) {
            var m = getRegexKey().matcher(promptKey);
            return new NameMatchFilter(m.find() ? m.group(1) : "");
        }

        @Override public List<Player> filter(Player relative) {
            return Bukkit.getOnlinePlayers().stream()
                    .<Player>map(p -> p)
                    .filter(p -> p.getName().equals(name))
                    .toList();
        }
    }
}
