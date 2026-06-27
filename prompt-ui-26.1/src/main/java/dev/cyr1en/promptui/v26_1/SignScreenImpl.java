package dev.cyr1en.promptui.v26_1;

import dev.cyr1en.promptui.ScreenResult;
import dev.cyr1en.promptui.SignInputScreen;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Material;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * NMS implementation of {@link SignInputScreen} that opens a virtual sign editor
 * for text input without placing a real block.
 *
 * <p>Sends a fake sign block state and block-entity data packet to the client, then
 * installs a {@link SignInterceptor} in the player's channel pipeline to capture
 * the sign-edit response. The virtual sign is always placed behind the player
 * so it stays hidden.</p>
 */
public class SignScreenImpl implements SignInputScreen, Listener {

    private final JavaPlugin plugin;
    private final Player player;
    private final String[] defaultLines;
    private BlockPos pos;
    private SignInterceptor interceptor;
    private Map<String, String> config = Map.of();
    private boolean open;
    private Consumer<ScreenResult> callback;

    public SignScreenImpl(JavaPlugin plugin, Player player, String[] lines) {
        this.plugin = plugin;
        this.player = player;
        this.defaultLines = lines;
        // Position is resolved at open() to track the player's current location.
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Computes the block 3 blocks behind the player at eye level. The sign
     * is always in a loaded chunk, at a valid Y coordinate within the build
     * limits, and (because the player is looking forward) hidden behind them.
     * This matches the SignGUI reference implementation and avoids the
     * out-of-bounds drop that breaks the editor-open packet when the sign
     * is placed above the build ceiling.
     */
    private BlockPos resolveSignPosition() {
        var eye = player.getEyeLocation();
        var dir = eye.getDirection();
        var x = eye.getBlockX() - (int) Math.round(dir.getX() * 3.0);
        var y = eye.getBlockY();
        var z = eye.getBlockZ() - (int) Math.round(dir.getZ() * 3.0);
        return new BlockPos(x, y, z);
    }

    /** {@inheritDoc} */
    @Override
    public void configure(Map<String, String> config) {
        this.config = new HashMap<>(config);
    }

    /**
     * Opens the virtual sign editor on the player's thread: resolves a hidden
     * position, sends fake block/entity packets, opens the sign GUI, and
     * installs a {@link SignInterceptor} to capture the response.
     */
    @Override
    public void open() {
        player.getScheduler().run(plugin, scheduledTask -> {
            var nmsPlayer = ((CraftPlayer) player).getHandle();
            pos = resolveSignPosition();

            var signState = resolveSignState();
            var signEntity = new SignBlockEntity(pos, signState);
            // Set text while level is null to prevent dirtying or saving the virtual chunk.
            var text = signEntity.getText(true);
            for (int i = 0; i < Math.min(defaultLines.length, 4); i++) {
                text = text.setMessage(i, Component.literal(defaultLines[i] != null ? defaultLines[i] : ""));
            }
            signEntity.setText(text, true);

            // Send block change, temporarily set level for the update packet, and open the sign editor.
            var signLocation = new org.bukkit.Location(
                    player.getWorld(), pos.getX(), pos.getY(), pos.getZ());
            player.sendBlockChange(signLocation, resolveSignMaterial().createBlockData());
            signEntity.setLevel(nmsPlayer.level());
            try {
                nmsPlayer.connection.send(signEntity.getUpdatePacket());
            } finally {
                signEntity.setLevel(null);
            }
            nmsPlayer.connection.send(new ClientboundOpenSignEditorPacket(pos, true));

            var pipeline = nmsPlayer.connection.connection.channel.pipeline();
            if (pipeline.get(SignInterceptor.HANDLER_NAME) != null) {
                pipeline.remove(SignInterceptor.HANDLER_NAME);
            }
            interceptor = new SignInterceptor(plugin, player, pos, this::handleSignFinish);
            pipeline.addAfter("decoder", SignInterceptor.HANDLER_NAME, interceptor);

            open = true;
            plugin.getSLF4JLogger().debug("SignScreen opened: player={} lines={} pos={}",
                    player.getName(), defaultLines.length, pos);
        }, null);
    }

    /** Converts the configured sign material to its default NMS {@link BlockState}. */
    private BlockState resolveSignState() {
        var nmsBlock = org.bukkit.craftbukkit.block.CraftBlockType
                .bukkitToMinecraft(resolveSignMaterial());
        return nmsBlock.defaultBlockState();
    }

    /** Resolves the sign {@link Material} from config, defaulting to {@link Material#OAK_SIGN}. */
    private Material resolveSignMaterial() {
        var materialName = config.getOrDefault("signMaterial", "OAK_SIGN");
        var mat = Material.matchMaterial(materialName);
        return mat != null ? mat : Material.OAK_SIGN;
    }

    /** Removes the {@link SignInterceptor} from the pipeline and restores the virtual block to air. */
    @Override
    public void close() {
        if (!open) return;
        open = false;
        plugin.getSLF4JLogger().debug("SignScreen closing: player={}", player.getName());
        player.getScheduler().run(plugin, scheduledTask -> {
            var nmsPlayer = ((CraftPlayer) player).getHandle();
            var pipeline = nmsPlayer.connection.connection.channel.pipeline();
            if (pipeline.get(SignInterceptor.HANDLER_NAME) != null) {
                pipeline.remove(SignInterceptor.HANDLER_NAME);
            }
            interceptor = null;
            // Remove fake sign from client
            nmsPlayer.connection.send(new ClientboundBlockUpdatePacket(pos, Blocks.AIR.defaultBlockState()));
        }, null);
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void onResult(Consumer<ScreenResult> callback) {
        this.callback = callback;
    }

    /** Cleans up the sign screen if the player disconnects while it is open. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (event.getPlayer().equals(player)) {
            plugin.getSLF4JLogger().debug("SignScreen player quit cleanup: player={}",
                    player.getName());
            close();
        }
    }

    /** Callback from {@link SignInterceptor}: removes the virtual sign and delivers the result. */
    private void handleSignFinish(String[] lines) {
        open = false;
        var nmsPlayer = ((CraftPlayer) player).getHandle();
        var pipeline = nmsPlayer.connection.connection.channel.pipeline();
        if (pipeline.get(SignInterceptor.HANDLER_NAME) != null) {
            pipeline.remove(SignInterceptor.HANDLER_NAME);
        }
        interceptor = null;
        nmsPlayer.connection.send(new ClientboundBlockUpdatePacket(pos, Blocks.AIR.defaultBlockState()));
        player.closeInventory();
        var answer = String.join("\n", lines).trim();
        plugin.getSLF4JLogger().debug("SignScreen finished: player={} answer={}",
                player.getName(), answer);
        if (callback != null) {
            if (answer.isEmpty()) {
                callback.accept(ScreenResult.cancel());
            } else {
                callback.accept(ScreenResult.answer(answer));
            }
        }
    }
}
