package dev.cyr1en.promptui.v26_1;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Netty channel handler that intercepts {@link ServerboundSignUpdatePacket} packets
 * matching a virtual sign position and routes the submitted lines to a callback.
 *
 * <p>Installed in the player's channel pipeline by {@link SignScreenImpl} and removed
 * when the sign screen closes. Matched packets are consumed (not forwarded) so the
 * server never processes a sign update at the virtual position.</p>
 */
public class SignInterceptor extends MessageToMessageDecoder<Packet<?>> {

    static final String HANDLER_NAME = "sign_interceptor";

    private final JavaPlugin plugin;
    private final Player player;
    private final BlockPos pos;
    private final Consumer<String[]> onFinish;

    public SignInterceptor(JavaPlugin plugin, Player player, BlockPos pos, Consumer<String[]> onFinish) {
        this.plugin = plugin;
        this.player = player;
        this.pos = pos;
        this.onFinish = onFinish;
    }

    /**
     * Intercepts sign-edit packets from the client. If the packet targets the
     * virtual position, the lines are dispatched to the callback on the player's
     * region thread and the packet is dropped; otherwise it is forwarded.
     */
    @Override
    protected void decode(ChannelHandlerContext ctx, Packet<?> packet, List<Object> out) {
        if (packet instanceof ServerboundSignUpdatePacket signPacket) {
            if (signPacket.getPos().equals(pos)) {
                var lines = signPacket.getLines();
                plugin.getSLF4JLogger().debug("SignInterceptor intercepted packet: player={} lines={}",
                        player.getName(), String.join(", ", lines));
                // route back to player's region thread
                player.getScheduler().run(plugin, scheduledTask -> {
                    onFinish.accept(lines);
                }, null);
                // Drop matched packet so the server does not process a sign
                // update at our (potentially far away) virtual position.
                return;
            }
        }
        out.add(packet);
    }
}
