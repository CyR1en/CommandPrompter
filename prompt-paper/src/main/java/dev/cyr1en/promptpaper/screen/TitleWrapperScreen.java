package dev.cyr1en.promptpaper.screen;

import dev.cyr1en.promptcore.TitleConfig;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.util.CancellableTask;
import dev.cyr1en.promptpaper.util.Scheduler;
import dev.cyr1en.promptui.ComponentUtil;
import dev.cyr1en.promptui.InputScreen;
import dev.cyr1en.promptui.ScreenResult;
import java.time.Duration;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

/**
 * A decorating {@link InputScreen} that shows an Adventure API on-screen title to the player for
 * a configurable number of ticks before opening the underlying (delegate) prompt screen.
 *
 * <p>The wrapper is inserted by {@link dev.cyr1en.promptpaper.factory.PromptFactory} whenever a
 * {@link PromptDefinition} carries a non-null {@link TitleConfig}. The title is sent immediately
 * in {@link #open()}, and the delegate screen is opened after {@code ticks} ticks (default 70)
 * via a {@link Scheduler#runLater} delayed task.
 *
 * <p>Lifecycle methods ({@link #onResult}, {@link #close}, {@link #isOpen}) are forwarded to the
 * delegate so the rest of the pipeline ( {@link ScreenManager}, listeners, etc.) is unaware of the
 * wrapper.
 */
public class TitleWrapperScreen implements InputScreen {

  /** Default ticks duration when {@link TitleConfig#ticks()} is {@code null}. */
  static final int DEFAULT_TICKS = 70;

  /** Fade-in / fade-out duration in ticks for the Adventure title. */
  private static final int FADE_TICKS = 10;

  private final InputScreen delegate;
  private final TitleConfig titleConfig;
  private final Player player;
  private final Scheduler scheduler;
  private final CommandPrompter plugin;

  private CancellableTask pendingTask;
  private boolean open;

  /**
   * @param delegate the underlying prompt screen to open after the title expires
   * @param titleConfig the title configuration (main, sub, ticks)
   * @param player the player who will see the title and the delegate screen
   * @param scheduler the Folia-safe scheduler used for the delayed delegate open
   * @param plugin the plugin reference (for logging)
   */
  public TitleWrapperScreen(
      InputScreen delegate,
      TitleConfig titleConfig,
      Player player,
      Scheduler scheduler,
      CommandPrompter plugin) {
    this.delegate = delegate;
    this.titleConfig = titleConfig;
    this.player = player;
    this.scheduler = scheduler;
    this.plugin = plugin;
  }

  /** The wrapped screen — exposed for testing and delegation checks. */
  public InputScreen delegate() {
    return delegate;
  }

  /**
   * Sends the Adventure title to the player and schedules the delegate screen to open after
   * {@code ticks} ticks.
   */
  @Override
  public void open() {
    var ticks = titleConfig.ticks() != null ? titleConfig.ticks() : DEFAULT_TICKS;
    var main = ComponentUtil.mini(titleConfig.main());
    if (main == null) main = Component.empty();
    var sub = ComponentUtil.mini(titleConfig.sub());
    if (sub == null) sub = Component.empty();

    var times =
        Title.Times.times(
            Duration.ofMillis(FADE_TICKS * 50L),
            Duration.ofMillis(ticks * 50L),
            Duration.ofMillis(FADE_TICKS * 50L));
    var title = Title.title(main, sub, times);
    player.showTitle(title);

    plugin.getPluginLogger().debug(
        "TitleWrapper: showing title for " + player.getName()
            + " ticks=" + ticks + " ticks, then opening " + delegate.getClass().getSimpleName());

    open = true;
    pendingTask =
        scheduler.runLater(
            () -> {
              pendingTask = null;
              delegate.open();
            },
            ticks);
  }

  /** Cancels any pending delegate open, clears the title, and closes the delegate. */
  @Override
  public void close() {
    open = false;
    if (pendingTask != null) {
      pendingTask.cancel();
      pendingTask = null;
    }
    player.clearTitle();
    delegate.close();
  }

  /**
   * Returns {@code true} once {@link #open()} has been called and until {@link #close()} is
   * called.
   */
  @Override
  public boolean isOpen() {
    return open;
  }

  /**
   * Forwards the result callback to the delegate screen so results flow directly to the
   * registered consumer.
   */
  @Override
  public void onResult(Consumer<ScreenResult> callback) {
    delegate.onResult(callback);
  }
}
