package dev.cyr1en.promptui.v26_1;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Netty channel handler that intercepts {@link ServerboundSignUpdatePacket} packets matching a
 * virtual sign position and routes the submitted lines to a callback.
 *
 * <p>Installed in the player's channel pipeline by {@link SignScreenImpl} and removed when the sign
 * screen closes. Matched packets are consumed (not forwarded) so the server never processes a sign
 * update at the virtual position.
 */
public class SignInterceptor extends MessageToMessageDecoder<Packet<?>> {

  private final JavaPlugin plugin;
  private final Player player;
  private final BlockPos pos;
  private final Consumer<String[]> onFinish;
  private final Consumer<ScheduledTask> onFinishTask;
  private final Consumer<Throwable> onScheduleFailure;
  private final AtomicBoolean packetQueued = new AtomicBoolean();

  public SignInterceptor(
      JavaPlugin plugin, Player player, BlockPos pos, Consumer<String[]> onFinish) {
    this(
        plugin,
        player,
        pos,
        "sign_interceptor_" + java.util.UUID.randomUUID(),
        onFinish,
        ignored -> {},
        ignored -> {});
  }

  public SignInterceptor(
      JavaPlugin plugin,
      Player player,
      BlockPos pos,
      String handlerName,
      Consumer<String[]> onFinish,
      Consumer<ScheduledTask> onFinishTask,
      Consumer<Throwable> onScheduleFailure) {
    this.plugin = plugin;
    this.player = player;
    this.pos = pos;
    this.onFinish = onFinish;
    this.onFinishTask = onFinishTask;
    this.onScheduleFailure = onScheduleFailure;
  }

  /**
   * Intercepts sign-edit packets from the client. If the packet targets the virtual position, the
   * lines are dispatched to the callback on the player's region thread and the packet is dropped;
   * otherwise it is forwarded.
   */
  @Override
  protected void decode(ChannelHandlerContext ctx, Packet<?> packet, List<Object> out) {
    if (packet instanceof ServerboundSignUpdatePacket signPacket
        && signPacket.getPos().equals(pos)) {
      if (!packetQueued.compareAndSet(false, true)) {
        return;
      }
      var lines = signPacket.getLines();
      plugin
          .getSLF4JLogger()
          .debug("SignInterceptor intercepted packet: player={}", player.getName());
      try {
        ScheduledTask task =
            player.getScheduler().run(plugin, scheduledTask -> onFinish.accept(lines), null);
        if (task == null) {
          onScheduleFailure.accept(
              new IllegalStateException("Player scheduler returned no sign-finish task"));
        } else {
          onFinishTask.accept(task);
        }
      } catch (Throwable failure) {
        onScheduleFailure.accept(failure);
      }
      // Drop the matched packet so the server never updates a real sign.
      return;
    }
    out.add(packet);
  }
}
