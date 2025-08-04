/*
 * MIT License
 *
 * Copyright (c) 2020 Ethan Bacurio
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.cyr1en.commandprompter.prompt.prompts;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.hook.hooks.VanishHook;
import com.cyr1en.commandprompter.prompt.PromptContext;
import com.cyr1en.commandprompter.prompt.PromptParser;
import com.cyr1en.commandprompter.prompt.ui.CacheFilter;
import com.cyr1en.commandprompter.prompt.ui.HeadCache;
import com.cyr1en.commandprompter.prompt.ui.inventory.ControlPane;
import com.cyr1en.commandprompter.util.Util;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import javax.annotation.Nullable;
import java.util.*;
import java.util.regex.Pattern;

import static com.cyr1en.commandprompter.util.AdventureUtil.*;

public class PlayerUIPrompt extends AbstractPrompt {

    private final int size;
    private final ChestGui gui;
    private final HeadCache headCache;
    private final VanishHook vanishHook;

    private final String promptKey;

    private boolean isSearching;

    public PlayerUIPrompt(CommandPrompter plugin, PromptContext context, String prompt,
            List<PromptParser.PromptArgument> args) {
        super(plugin, context, prompt, args);
        var cfgSize = getPlugin().getPromptConfig().playerUISize();
        var parts = Arrays.asList(getPrompt().split("\\{br}"));
        size = Math.max((cfgSize - (cfgSize % 9)) / 9, 2);
        gui = new ChestGui(size, legacyColor(parts.get(0)));
        this.headCache = plugin.getHeadCache();
        vanishHook = plugin.getHookContainer().getVanishHook().get();
        this.promptKey = context.getPromptKey();
        this.isSearching = false;
    }

    private List<Player> getPlayersForHeads(List<CacheFilter> filters, Player p) {
        if (filters.isEmpty())
            return (List<Player>) Bukkit.getOnlinePlayers();

        var pattern = Pattern.compile(headCache.makeFilteredPattern());
        getPlugin().getPluginLogger().debug("Pattern: " + pattern.pattern());
        var matcher = pattern.matcher(this.promptKey);
        if (!matcher.find()) {
            getPlugin().getPluginLogger().debug("No match found!");
            return (List<Player>) Bukkit.getOnlinePlayers();
        }

        getPlugin().getPluginLogger().debug("Filters: " + filters);

        // for all filters get the players that exist on all filters
        if (filters.size() == 1)
            return filters.get(0).filter(p);

        // Intersect all filters.filter
        var intersect = new HashSet<>(filters.get(0).filter(p));
        for (int i = 1; i < filters.size(); i++) {
            var set2 = new HashSet<>(filters.get(i).filter(p));
            intersect.retainAll(set2);
        }

        return intersect.stream().toList();
    }

    private List<CacheFilter> extractFilters() {
        var pattern = Pattern.compile(headCache.makeFilteredPattern());
        var matcher = pattern.matcher(this.promptKey);
        if (!matcher.find()) {
            getPlugin().getPluginLogger().debug("No match found!");
            return List.of();
        }
        // debug all cap groups
        for (int i = 0; i <= matcher.groupCount(); i++) {
            getPlugin().getPluginLogger().debug("Group {0}: {1}", i, matcher.group(i));
        }

        var extractedFilters = new ArrayList<CacheFilter>();
        for (var filter : headCache.getFilters()) {
            var capGroup = getCapturingGroup(filter);
            getPlugin().getPluginLogger().debug("Capturing group: " + capGroup);
            var filterKey = matcher.group(capGroup);
            if (Objects.isNull(filterKey))
                continue;
            extractedFilters.add(filter.reConstruct(promptKey));
        }
        return extractedFilters;
    }

    private CacheFilter getFirstFilter(List<CacheFilter> filters) {
        var keyStripped = promptKey.replace("p:", "");
        if (filters.isEmpty())
            return null;
        if (filters.size() == 1)
            return filters.get(0);

        var firstFilter = filters.get(0);
        var idx = 0;
        var matcher = firstFilter.getRegexKey().matcher(keyStripped);
        if (matcher.find()) {
            idx = matcher.start();
        }
        for (int i = 1; i < filters.size(); i++) {
            var filter = filters.get(i);
            var matcher2 = filter.getRegexKey().matcher(keyStripped);
            if (matcher2.find()) {
                var idx2 = matcher2.start();
                if (idx2 < idx) {
                    idx = idx2;
                    firstFilter = filter;
                }
            }
        }
        return firstFilter;
    }

    private int getCapturingGroup(CacheFilter cacheFilter) {
        getPlugin().getPluginLogger().debug("Getting capturing group for filter: " + cacheFilter.getRegexKey());
        var idx = 2; // index starts at 2 because 0 is the whole match and 1 is just a blank.
        for (var filter : headCache.getFilters()) {
            if (filter.equals(cacheFilter))
                return idx;
            idx = idx + filter.getCapGroupOffset() + 1;
        }
        return -1;
    }

    private void send(Player p) {
        gui.setOnClose(e -> {
            if (!isSearching)
                getPromptManager().cancel(p);
        });

        var skullPane = new PaginatedPane(0, 0, 9, size - 1);

        var skulls = prepareHeads(p);
        var headCacheStr = headCache.toString();
        getPlugin().getPluginLogger().debug("Head Cache: " + headCacheStr.substring(headCacheStr.indexOf('@')));
        getPlugin().getPluginLogger().debug("|-- Size: " + headCache.getHeads().size());

        skullPane.populateWithItemStacks(skulls);
        skullPane.setOnClick(this::processClick);

        gui.addPane(skullPane);
        gui.addPane(new ControlPane(getPlugin(), skullPane, gui, getContext(), size, this));

        gui.show(getContext().getPromptedPlayer());
    }

    private List<ItemStack> prepareHeads(Player p) {
        getPlugin().getPluginLogger().debug("Preparing heads...");
        var filters = extractFilters();
        var players = getPlayersForHeads(filters, p).stream().filter(player -> !vanishHook.isInvisible(player))
                .toList();
        var isSorted = getPlugin().getPromptConfig().sorted();
        var skulls = isSorted ? headCache.getHeadsSortedFor(players) : headCache.getHeadsFor(players);
        var clone = new ArrayList<>(skulls);
        formatHeads(clone, getFirstFilter(filters));
        debugHeads(clone);
        return clone;
    }

    private void debugHeads(List<ItemStack> is) {
        for (ItemStack itemStack : is) {
            var meta = (SkullMeta) itemStack.getItemMeta();
            getPlugin().getPluginLogger().debug("Head: " + meta.getDisplayName());
        }
    }

    private void formatHeads(List<ItemStack> heads, @Nullable CacheFilter filter) {
        getPlugin().getPluginLogger().debug("Formatting heads...");
        var config = getPlugin().getPromptConfig();
        var format = Objects.isNull(filter) ? config.skullNameFormat() : filter.getFormat(config);
        for (ItemStack head : heads) {
            var meta = (SkullMeta) head.getItemMeta();
            if (Objects.isNull(meta))
                continue;
            headCache.setDisplayName(meta, format);
            head.setItemMeta(meta);
        }
    }

    public void setSearching(boolean searching) {
        isSearching = searching;
    }

    @Override
    public void sendPrompt() {
        var p = getContext().getPromptedPlayer();

        var missingCached = Bukkit.getOnlinePlayers().stream().filter(player -> !vanishHook.isInvisible(player))
                .count() > headCache.getHeads().size();
        if (missingCached) {
            getPlugin().getPluginLogger().debug("Missing heads in cache, rebuilding before sending...");
            headCache.reBuildCache().thenAccept(cache -> {
                getPlugin().getPluginLogger().debug("Rebuilt cache!");
                Bukkit.getScheduler().runTask(getPlugin(), () -> send(p));
            });
        } else {
            send(p);
        }

    }

    private void processClick(InventoryClickEvent e) {
        e.setCancelled(true);
        if (Objects.isNull(e.getCurrentItem()))
            return;
        var name = Objects.requireNonNull(
                Objects.requireNonNull(Objects.requireNonNull((SkullMeta) (e.getCurrentItem()).getItemMeta()))
                        .getOwningPlayer())
                .getName();
        name = plain(name);
        var ctx = new PromptContext.Builder()
                .setCommandSender(getContext().getCommandSender())
                .setPromptedPlayer(getContext().getPromptedPlayer())
                .setContent(name).build();
        getPlugin().getPromptManager().processPrompt(ctx);
        gui.setOnClose(null);
        getContext().getPromptedPlayer().closeInventory();
    }
}
