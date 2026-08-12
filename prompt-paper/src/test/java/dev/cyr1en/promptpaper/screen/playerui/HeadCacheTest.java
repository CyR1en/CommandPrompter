package dev.cyr1en.promptpaper.screen.playerui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.config.PromptConfig;
import dev.cyr1en.promptpaper.hook.HookContainer;
import dev.cyr1en.promptpaper.hook.hooks.VanishHook;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for the PlayerUI head-cache empty-entry bug.
 *
 * <p>Originally the cache could be left in a state where
 * {@code size()} matched the online player count but every entry was
 * {@code Optional.empty()}, causing {@link dev.cyr1en.promptpaper.screen.playerui.PlayerUIScreen}
 * to open with no heads. Two fixes are exercised here:
 *
 * <ol>
 *   <li>{@code size()} counts only non-empty entries, so the staleness
 *       check in {@code PlayerUIScreen.open()} detects a half-built cache.</li>
 *   <li>{@code onPlayerJoin} uses {@link PlayerJoinEvent} (not
 *       {@code PlayerLoginEvent}), so the player is already in
 *       {@code Bukkit.getOnlinePlayers()} when {@code getHeadFor} runs.</li>
 * </ol>
 */
class HeadCacheTest extends MockBukkitTest {

    private HeadCache headCache;
    private PromptConfig promptCfg;

    @BeforeEach
    void setUpHeadCache() {
        promptCfg = mock(PromptConfig.class);
        when(promptCfg.cacheDelay()).thenReturn(0);
        when(promptCfg.skullNameFormat()).thenReturn("&6%s");
        when(promptCfg.skullCustomModelData()).thenReturn(0);
        when(configLoader.getPromptConfig()).thenReturn(promptCfg);

        headCache = new HeadCache(plugin, scheduler);
    }

    @Test
    void sizeReturnsZeroForEmptyCache() {
        assertEquals(0, headCache.size());
        assertTrue(headCache.getHeads().isEmpty());
    }

    @Test
    void sizeIgnoresEmptyOptionalEntries() {
        // Simulate the regression: cache holds only Optional.empty() because
        // getHeadFor was called before the player joined.
        UUID uuid = UUID.randomUUID();
        injectEmptyEntry(uuid);

        assertEquals(0, headCache.size(),
                "size() must ignore Optional.empty() entries so the staleness check fires");
        assertTrue(headCache.getHeads().isEmpty());
    }

    @Test
    void sizeCountsNonEmptyEntries() {
        var player = createPlayer("CachedOne");
        injectEntry(player.getUniqueId(), Optional.of(new ItemStack(org.bukkit.Material.PLAYER_HEAD)));

        assertEquals(1, headCache.size());
        assertEquals(1, headCache.getHeads().size());
    }

    @Test
    void sizeCountsMixedEntries() {
        var player = createPlayer("CachedMixed");
        UUID emptyUuid = UUID.randomUUID();
        injectEntry(player.getUniqueId(), Optional.of(new ItemStack(org.bukkit.Material.PLAYER_HEAD)));
        injectEmptyEntry(emptyUuid);

        assertEquals(1, headCache.size(),
                "size() should only count the populated entry");
        assertEquals(1, headCache.getHeads().size());
    }

    @Test
    void onPlayerJoinPopulatesCache() {
        var player = createPlayer("VisibleOne");

        headCache.onPlayerJoin(new PlayerJoinEvent(player, (Component) null));

        assertEquals(1, headCache.size());
        assertFalse(headCache.getHeads().isEmpty());
    }

    @Test
    void onPlayerQuitRemovesCachedHead() {
        var player = createPlayer("Quitter");
        headCache.onPlayerJoin(new PlayerJoinEvent(player, (Component) null));
        assertEquals(1, headCache.size());

        headCache.onPlayerQuit(new PlayerQuitEvent(player, (Component) null));

        assertEquals(0, headCache.size());
        assertTrue(headCache.getHeads().isEmpty());
    }

    @Test
    void invalidateRemovesEntry() {
        var player = createPlayer("Target");
        headCache.onPlayerJoin(new PlayerJoinEvent(player, (Component) null));
        assertEquals(1, headCache.size());

        headCache.invalidate(player);

        assertEquals(0, headCache.size());
    }

    /**
     * Regression test for the legacy code rendering bug: when {@code skullNameFormat} contains a
     * legacy {@code &}-code (e.g. {@code &6%s}), the resulting skull's display name Component must
     * carry the color — not the literal {@code &6} characters.
     */
    @Test
    void getHeadForRendersLegacyColorCode() {
        when(promptCfg.skullNameFormat()).thenReturn("&6%s");
        var player = createPlayer("GoldSkull");

        headCache.onPlayerJoin(new PlayerJoinEvent(player, (Component) null));

        Optional<ItemStack> head = headCache.getHeadFor(player);
        assertTrue(head.isPresent());
        var meta = head.get().getItemMeta();
        assertNotNull(meta);
        Component display = meta.displayName();
        assertNotNull(display, "displayName() must be set");
        Style coloredStyle = findStyleWithColor(display);
        assertNotNull(coloredStyle, "expected a style with a color in the component tree");
        assertEquals(NamedTextColor.GOLD, coloredStyle.color(),
                "&6 should render as GOLD, not literal text");
    }

    /**
     * Companion to {@link #getHeadForRendersLegacyColorCode()} — verifies that pure MiniMessage
     * input renders correctly too (no regression on the format we already supported).
     */
    @Test
    void getHeadForRendersMiniMessage() {
        when(promptCfg.skullNameFormat()).thenReturn("<gold>%s");
        var player = createPlayer("MiniSkull");

        headCache.onPlayerJoin(new PlayerJoinEvent(player, (Component) null));

        Optional<ItemStack> head = headCache.getHeadFor(player);
        assertTrue(head.isPresent());
        var meta = head.get().getItemMeta();
        assertNotNull(meta);
        Component display = meta.displayName();
        assertNotNull(display);
        Style coloredStyle = findStyleWithColor(display);
        assertNotNull(coloredStyle);
        assertEquals(NamedTextColor.GOLD, coloredStyle.color());
    }

    @Test
    void buildCacheWaitsForAllBatchedPlayerTasksBeforeCompletion() {
        for (int i = 0; i < 26; i++) createPlayer("Batch" + i);
        var callbackCount = new AtomicInteger();

        headCache.buildCache(callbackCount::incrementAndGet);

        assertEquals(0, callbackCount.get());
        performOneTick();
        assertEquals(1, callbackCount.get());
        assertEquals(26, headCache.size());
    }

    @Test
    void getHeadForReturnsEmptyForVanishedPlayer() {
        var player = createPlayer("Ghost");
        mockVanishHook(player);

        Optional<ItemStack> head = headCache.getHeadFor(player);

        assertTrue(head.isEmpty(), "vanished players must not receive a head");
        assertEquals(0, headCache.size(), "vanished players must not be cached");
    }

    @Test
    void getHeadForAfterUnvanishCreatesHead() {
        var player = createPlayer("Phasing");
        var vanishHook = mockVanishHook(player);

        assertTrue(headCache.getHeadFor(player).isEmpty());

        when(vanishHook.isInvisible(player)).thenReturn(false);

        assertTrue(headCache.getHeadFor(player).isPresent(),
                "an unvanished player must receive a cached head again");
        assertEquals(1, headCache.size());
    }

    @Test
    void getHeadsExcludesCachedVanishedPlayer() {
        var visible = createPlayer("Visible");
        var ghost = createPlayer("Ghost");
        headCache.onPlayerJoin(new PlayerJoinEvent(visible, (Component) null));
        headCache.onPlayerJoin(new PlayerJoinEvent(ghost, (Component) null));
        assertEquals(2, headCache.size());

        mockVanishHook(ghost);

        assertEquals(1, headCache.getHeads().size(),
                "getHeads() must exclude players that vanished after being cached");
        assertEquals(1, headCache.getHeadsSorted().size(),
                "getHeadsSorted() must exclude players that vanished after being cached");
    }

    @Test
    void buildCacheExcludesVanishedPlayers() {
        createPlayer("Visible");
        var ghost = createPlayer("Ghost");
        mockVanishHook(ghost);

        var callbackCount = new AtomicInteger();
        headCache.buildCache(callbackCount::incrementAndGet);

        assertEquals(1, callbackCount.get());
        assertEquals(1, headCache.size(), "buildCache() must not cache vanished players");
    }

    /**
     * Installs a mocked vanish hook on the plugin's hook container that
     * reports the given players as vanished.
     */
    private VanishHook mockVanishHook(Player... vanishedPlayers) {
        var vanishHook = mock(VanishHook.class);
        for (Player vanished : vanishedPlayers) {
            when(vanishHook.isInvisible(vanished)).thenReturn(true);
        }
        var hookContainer = mock(HookContainer.class);
        when(hookContainer.getFirstHooked(VanishHook.class)).thenReturn(Optional.of(vanishHook));
        when(plugin.getHookContainer()).thenReturn(hookContainer);
        return vanishHook;
    }

    private static Style findStyleWithColor(Component root) {
        if (root.style().color() != null) return root.style();
        for (Component child : root.children()) {
            Style found = findStyleWithColor(child);
            if (found != null) return found;
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "PMD.AvoidAccessibilityAlteration"})
    private void injectEmptyEntry(UUID uuid) {
        injectEntry(uuid, Optional.empty());
    }

    @SuppressWarnings({"unchecked", "PMD.AvoidAccessibilityAlteration"})
    private void injectEntry(UUID uuid, Optional<ItemStack> value) {
        try {
            Field field = HeadCache.class.getDeclaredField("cache");
            field.setAccessible(true);
            Map<UUID, Optional<ItemStack>> map =
                    (Map<UUID, Optional<ItemStack>>) field.get(headCache);
            map.put(uuid, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to inject cache entry", e);
        }
    }
}
