package dev.cyr1en.promptui.v26_3;

import dev.cyr1en.promptui.ScreenResult;
import dev.cyr1en.promptui.SignInputScreen;
import dev.cyr1en.promptui.util.ClientBlockRestoration;
import io.netty.channel.ChannelPipeline;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Material;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** NMS implementation of {@link SignInputScreen} for a virtual sign editor. */
public class SignScreenImpl implements SignInputScreen, Listener {

  private final JavaPlugin plugin;
  private final Player player;
  private final String[] defaultLines;
  private final String handlerName = "commandprompter_sign_" + UUID.randomUUID();
  private final Object lifecycleLock = new Object();
  private BlockPos pos;
  private SignInterceptor interceptor;
  private Map<String, String> config = Map.of();
  private Consumer<ScreenResult> callback;
  private Consumer<Throwable> openFailure;
  private ScheduledTask openTask;
  private ScheduledTask finishTask;
  private ClientBlockRestoration blockRestoration;
  private State state = State.NEW;

  private enum State {
    NEW,
    OPENING,
    OPEN,
    CLOSED
  }

  public SignScreenImpl(JavaPlugin plugin, Player player, String[] lines) {
    this.plugin = plugin;
    this.player = player;
    this.defaultLines = lines.clone();
    plugin.getServer().getPluginManager().registerEvents(this, plugin);
  }

  private BlockPos resolveSignPosition() {
    var eye = player.getEyeLocation();
    var dir = eye.getDirection();
    var x = eye.getBlockX() - (int) Math.round(dir.getX() * 3.0);
    var y = eye.getBlockY();
    var z = eye.getBlockZ() - (int) Math.round(dir.getZ() * 3.0);
    int minY = player.getWorld().getMinHeight();
    int maxY = player.getWorld().getMaxHeight() - 1;
    y = Math.max(minY, Math.min(maxY, y));
    return new BlockPos(x, y, z);
  }

  @Override
  public void configure(Map<String, String> config) {
    this.config = new HashMap<>(config);
  }

  @Override
  public void open() {
    synchronized (lifecycleLock) {
      if (state != State.NEW) return;
      state = State.OPENING;
    }
    ScheduledTask scheduled;
    try {
      scheduled =
          player
              .getScheduler()
              .run(
                  plugin,
                  ignored -> openOnPlayerThread(),
                  () -> failOpen(new IllegalStateException("Player scheduler retired")));
    } catch (Throwable failure) {
      failOpen(failure);
      return;
    }
    synchronized (lifecycleLock) {
      if (state == State.OPENING) {
        openTask = scheduled;
      } else if (scheduled != null) {
        scheduled.cancel();
      }
    }
    if (scheduled == null) {
      failOpen(new IllegalStateException("Player scheduler returned no task"));
    }
  }

  private void openOnPlayerThread() {
    synchronized (lifecycleLock) {
      if (state != State.OPENING) return;
      openTask = null;
    }
    try {
      var nmsPlayer = ((CraftPlayer) player).getHandle();
      pos = resolveSignPosition();

      var signState = resolveSignState();
      var signEntity = new SignBlockEntity(pos, signState);
      var text = signEntity.getText(SignTextSlot.FRONT).asMutable();
      for (int i = 0; i < Math.min(defaultLines.length, 4); i++) {
        text.setLine(i, Component.literal(defaultLines[i] != null ? defaultLines[i] : ""));
      }
      signEntity.setText(text.asImmutable(), SignTextSlot.FRONT);

      var signLocation =
          new org.bukkit.Location(player.getWorld(), pos.getX(), pos.getY(), pos.getZ());
      blockRestoration = new ClientBlockRestoration(plugin, player, signLocation);
      player.sendBlockChange(signLocation, resolveSignMaterial().createBlockData());
      signEntity.setLevel(nmsPlayer.level());
      try {
        nmsPlayer.connection.send(signEntity.getUpdatePacket());
      } finally {
        signEntity.setLevel(null);
      }

      ChannelPipeline pipeline = nmsPlayer.connection.connection.channel.pipeline();
      if (pipeline == null || pipeline.get("decoder") == null) {
        throw new IllegalStateException("Player connection decoder is unavailable");
      }
      interceptor =
          new SignInterceptor(
              plugin,
              player,
              pos,
              handlerName,
              this::handleSignFinish,
              this::rememberFinishTask,
              this::failOpen);
      pipeline.addAfter("decoder", handlerName, interceptor);
      synchronized (lifecycleLock) {
        if (state != State.OPENING) {
          cleanupScreen(false);
          return;
        }
        state = State.OPEN;
      }
      nmsPlayer.connection.send(new ClientboundOpenSignEditorPacket(pos, SignTextSlot.FRONT));
      plugin
          .getSLF4JLogger()
          .debug(
              "SignScreen opened: player={} lines={} pos={}",
              player.getName(),
              defaultLines.length,
              pos);
    } catch (Throwable failure) {
      failOpen(failure);
    }
  }

  private void rememberFinishTask(ScheduledTask task) {
    synchronized (lifecycleLock) {
      if (state == State.OPEN || state == State.OPENING) {
        finishTask = task;
        return;
      }
    }
    task.cancel();
  }

  private BlockState resolveSignState() {
    var nmsBlock =
        org.bukkit.craftbukkit.block.CraftBlockType.bukkitToMinecraft(resolveSignMaterial());
    return nmsBlock.defaultBlockState();
  }

  private Material resolveSignMaterial() {
    var materialName = config.getOrDefault("signMaterial", "OAK_SIGN");
    var mat = Material.matchMaterial(materialName);
    return mat != null ? mat : Material.OAK_SIGN;
  }

  @Override
  public void close() {
    ScheduledTask open;
    ScheduledTask finish;
    synchronized (lifecycleLock) {
      if (state == State.CLOSED) return;
      state = State.CLOSED;
      open = openTask;
      finish = finishTask;
      openTask = null;
      finishTask = null;
      callback = null;
      openFailure = null;
    }
    cancel(open);
    cancel(finish);
    HandlerList.unregisterAll(this);
    scheduleCleanup(false, null);
  }

  @Override
  public boolean isOpen() {
    synchronized (lifecycleLock) {
      return state == State.OPEN;
    }
  }

  @Override
  public void onResult(Consumer<ScreenResult> callback) {
    this.callback = callback;
  }

  @Override
  public void onOpenFailure(Consumer<Throwable> callback) {
    this.openFailure = callback;
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    if (event.getPlayer().equals(player)) close();
  }

  private void handleSignFinish(String[] lines) {
    Consumer<ScreenResult> resultCallback;
    ScheduledTask finish;
    synchronized (lifecycleLock) {
      if (state != State.OPEN) return;
      state = State.CLOSED;
      finish = finishTask;
      finishTask = null;
      resultCallback = callback;
      callback = null;
    }
    cancel(finish);
    HandlerList.unregisterAll(this);
    String answer = String.join("\n", lines == null ? new String[0] : lines);
    ScreenResult result =
        answer.isBlank() ? ScreenResult.blankInput() : ScreenResult.answer(answer);
    try {
      cleanupScreen(true);
    } finally {
      try {
        if (resultCallback != null) resultCallback.accept(result);
      } finally {
        cleanupScreen(false);
      }
    }
  }

  private void failOpen(Throwable failure) {
    Consumer<Throwable> failureCallback;
    ScheduledTask open;
    ScheduledTask finish;
    synchronized (lifecycleLock) {
      if (state == State.CLOSED) return;
      state = State.CLOSED;
      open = openTask;
      finish = finishTask;
      openTask = null;
      finishTask = null;
      failureCallback = openFailure;
      openFailure = null;
      callback = null;
    }
    cancel(open);
    cancel(finish);
    HandlerList.unregisterAll(this);
    scheduleCleanup(false, failureCallback == null ? null : () -> failureCallback.accept(failure));
  }

  private void scheduleCleanup(boolean closeInventory, Runnable afterCleanup) {
    try {
      ScheduledTask task =
          player
              .getScheduler()
              .run(
                  plugin,
                  ignored -> terminalCleanup(closeInventory, afterCleanup),
                  () -> terminalCleanup(closeInventory, afterCleanup));
      if (task == null) terminalCleanup(closeInventory, afterCleanup);
    } catch (Throwable failure) {
      terminalCleanup(closeInventory, afterCleanup);
    }
  }

  private void terminalCleanup(boolean closeInventory, Runnable afterCleanup) {
    try {
      cleanupScreen(closeInventory);
    } finally {
      if (afterCleanup != null) {
        try {
          afterCleanup.run();
        } finally {
          cleanupScreen(false);
        }
      }
    }
  }

  private void cleanupScreen(boolean closeInventory) {
    SignInterceptor current = interceptor;
    interceptor = null;
    HandlerList.unregisterAll(this);
    try {
      var nmsPlayer = ((CraftPlayer) player).getHandle();
      if (nmsPlayer.connection != null && nmsPlayer.connection.connection != null) {
        ChannelPipeline pipeline = nmsPlayer.connection.connection.channel.pipeline();
        if (pipeline != null && current != null && pipeline.get(handlerName) == current) {
          pipeline.remove(handlerName);
        }
      }
    } catch (Throwable failure) {
      plugin.getSLF4JLogger().debug("Sign pipeline cleanup failed: {}", failure.getMessage());
    } finally {
      restoreClientState();
      if (closeInventory) {
        try {
          player.closeInventory();
        } catch (Throwable failure) {
          plugin.getSLF4JLogger().debug("Sign inventory cleanup failed: {}", failure.getMessage());
        }
      }
    }
  }

  private void restoreClientState() {
    ClientBlockRestoration restoration;
    synchronized (lifecycleLock) {
      restoration = blockRestoration;
      blockRestoration = null;
    }
    if (restoration != null) restoration.restore();
  }

  private static void cancel(ScheduledTask task) {
    if (task != null) task.cancel();
  }
}
