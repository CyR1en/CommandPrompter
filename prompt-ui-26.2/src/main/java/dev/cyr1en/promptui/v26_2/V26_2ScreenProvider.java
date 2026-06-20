package dev.cyr1en.promptui.v26_2;

import dev.cyr1en.promptui.InputScreen;
import dev.cyr1en.promptui.ScreenProvider;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * MC 26.2 screen provider using the framework-backed {@link FrameworkAnvilScreen}
 * and the existing NMS-based {@link SignScreenImpl}.
 */
public class V26_2ScreenProvider implements ScreenProvider {

    @Override
    public InputScreen createAnvil(JavaPlugin plugin, Player player, String text) {
        return new FrameworkAnvilScreen(plugin, player, text);
    }

    @Override
    public InputScreen createSign(JavaPlugin plugin, Player player, String[] lines) {
        return new SignScreenImpl(plugin, player, lines);
    }

    @Override
    public String getTargetVersion() {
        return "26.2";
    }
}
