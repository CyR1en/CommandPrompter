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
import com.cyr1en.commandprompter.prompt.PromptContext;
import com.cyr1en.commandprompter.prompt.PromptParser;
import com.cyr1en.commandprompter.util.ServerUtil;
import com.cyr1en.commandprompter.util.Util;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class AnvilPrompt extends AbstractPrompt {

    public static char BLANK_CHAR = '\u00A0'; // No-Break Space

    public AnvilPrompt(CommandPrompter plugin, PromptContext context,
                       String prompt, List<PromptParser.PromptArgument> args) {
        super(plugin, context, prompt, args);
    }

    @Override
    public void sendPrompt() {
        List<String> parts = Arrays.asList(getPrompt().split("\\{br}"));
        var item = makeAnvilItem(parts);
        var resultItem = makeResultItem(parts);
        var cancelItem = makeCancelItem(parts);
        makeAnvil(parts, item, resultItem, cancelItem).open((Player) getContext().getSender());
    }

    private AnvilGUI.Builder makeAnvil(List<String> parts, ItemStack item, ItemStack resultItem, ItemStack cancelItem) {
        var isComplete = new AtomicBoolean(false);
        var builder = getBuilder(isComplete);
        builder.onClose(p -> {
            if (isComplete.get())
                return;
            getPromptManager().cancel(p.getPlayer());
        });

        var promptText = getPlugin().getPromptConfig().promptMessage();

        var text = (promptText.isBlank()) ? color(parts.get(0)) : promptText.equals("BLANK") ?
                String.valueOf(BLANK_CHAR) : color(promptText);
        builder.text(text);

        if (getPlugin().getPromptConfig().enableTitle()) {
            var title = getPlugin().getPromptConfig().customTitle();
            title = title.isEmpty() ? color(parts.get(0)) : color(title);
            builder.title(title);
        }

        if (getPlugin().getPromptConfig().enableCancelItem())
            builder.itemRight(cancelItem);

        builder.itemLeft(item);
        builder.itemOutput(resultItem);
        builder.plugin(getPlugin());
        return builder;
    }

    @NotNull
    private AnvilGUI.Builder getBuilder(AtomicBoolean isComplete) {
        var builder = new AnvilGUI.Builder();
        builder.onClick((slot, stateSnapshot) -> {
            var cancelEnabled = getPlugin().getPromptConfig().enableCancelItem();
            if (slot == AnvilGUI.Slot.INPUT_RIGHT && cancelEnabled) {
                getPromptManager().cancel(stateSnapshot.getPlayer());
                return Collections.singletonList(AnvilGUI.ResponseAction.close());
            }

            if (slot != AnvilGUI.Slot.OUTPUT)
                return Collections.emptyList();

            var message = ChatColor.stripColor(
                    ChatColor.translateAlternateColorCodes('&', stateSnapshot.getText()));
            var cancelKeyword = getPlugin().getConfiguration().cancelKeyword();
            if (cancelKeyword.equalsIgnoreCase(message)) {
                getPromptManager().cancel(stateSnapshot.getPlayer());
                return Collections.singletonList(AnvilGUI.ResponseAction.close());
            }

            isComplete.getAndSet(true);
            var content = stateSnapshot.getText().replaceAll(String.valueOf(BLANK_CHAR), "");
            var ctx = new PromptContext.Builder()
                    .setSender(stateSnapshot.getPlayer())
                    .setContent(content).build();

            getPromptManager().processPrompt(ctx);
            return Collections.singletonList(AnvilGUI.ResponseAction.close());
        });
        return builder;
    }

    private ItemStack makeItem(String prefix, List<String> parts, String customTitle) {
        var config = getPlugin().getPromptConfig().rawConfig();
        var material = config.getString(prefix + ".Material", "PAPER");
        var enchanted = config.getBoolean(prefix + ".Enchanted", false);
        var customModelData = config.getInt(prefix + ".Custom-Model-Data", 0);
        var hideTooltips = config.getBoolean(prefix + ".HideTooltips", false);

        var item = new ItemStack(Util.getCheckedMaterial(material, Material.PAPER));
        var meta = item.getItemMeta();
        getPlugin().getPluginLogger().debug("ItemMeta: " + meta);

        if (enchanted) {
            Objects.requireNonNull(meta).addEnchant(Enchantment.LURE, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        Objects.requireNonNull(meta).addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (customTitle != null)
            meta.setDisplayName(customTitle);
        else {
            meta.setDisplayName(parts.get(0));

            meta.setDisplayName(parts.get(0));

            if (parts.size() > 1)
                meta.setLore(parts.subList(1, parts.size()).stream().map(this::color).toList());

            if (customModelData != 0)
                meta.setCustomModelData(customModelData);

            if (ServerUtil.isAtOrAbove("1.21.2")) {
                meta.setHideTooltip(hideTooltips);
            }
        }

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeItem(String prefix, List<String> parts) {
        return makeItem(prefix, parts, null);
    }

    private ItemStack makeAnvilItem(List<String> parts) {
        return makeItem("AnvilGUI.Item", parts);
    }

    private ItemStack makeResultItem(List<String> parts) {
        return makeItem("AnvilGUI.ResultItem", parts);
    }

    private ItemStack makeCancelItem(List<String> parts) {
        var cancelText = getPlugin().getPromptConfig().cancelItemHoverText();
        return makeItem("AnvilGUI.CancelItem", parts, Util.color(cancelText));
    }
}
