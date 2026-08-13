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
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
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
        var filteredScreen = new PlayerUIScreen(plugin, player, filteredTag, null);
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
     * Custom filter mimicking a hook's parameterized filter: matches
     * {@code zz<name>;} tokens and returns only online players whose name
     * equals the captured parameter.
     */
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
