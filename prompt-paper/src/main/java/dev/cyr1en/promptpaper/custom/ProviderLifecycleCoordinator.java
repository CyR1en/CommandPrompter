package dev.cyr1en.promptpaper.custom;

import dev.cyr1en.promptcore.CancelReason;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.screen.ScreenManager;
import dev.cyr1en.promptui.InputScreen;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Coordinates graceful teardown of custom screen providers during plugin disable.
 *
 * <p>Enforces atomic registry detachment before player scheduling, cancel-before-close ordering,
 * zero provider delegate calls after teardown, best-effort platform UI closure, and audit logging.
 *
 * <h2>Platform UI Closure &amp; Provider Limitations</h2>
 *
 * <p>CommandPrompter performs a best-effort {@link Player#closeInventory()} attempt for online
 * players whose entity scheduler accepts work. It does NOT guarantee closure of Paper dialogs,
 * signs, protocol UIs, arbitrary non-inventory UI, offline players, or retired schedulers.
 * Providers own non-inventory/critical external cleanup and must not depend on {@link
 * InputScreen#close()} during disable, as third-party delegate methods are strictly NOT called once
 * provider teardown has commenced.
 */
public class ProviderLifecycleCoordinator {

  private final CustomScreenRegistry registry;
  private final ScreenManager screenManager;
  private final CustomScreenAuditLogger auditLogger;
  private final Function<Player, PlayerExecutor> playerExecutorFactory;

  /**
   * Constructs a new {@link ProviderLifecycleCoordinator} with an injected player executor factory.
   *
   * @param registry the custom screen registry
   * @param screenManager the screen manager tracking active sessions and handles
   * @param auditLogger consumer for lifecycle audit events
   * @param playerExecutorFactory factory creating {@link PlayerExecutor} for a player
   */
  public ProviderLifecycleCoordinator(
      CustomScreenRegistry registry,
      ScreenManager screenManager,
      CustomScreenAuditLogger auditLogger,
      Function<Player, PlayerExecutor> playerExecutorFactory) {
    this.registry = Objects.requireNonNull(registry, "registry");
    this.screenManager = Objects.requireNonNull(screenManager, "screenManager");
    this.auditLogger = auditLogger != null ? auditLogger : CustomScreenAuditLogger.noop();
    this.playerExecutorFactory =
        playerExecutorFactory != null
            ? playerExecutorFactory
            : player -> PlayerExecutor.forPlayer(null, player);
  }

  /**
   * Constructs a {@link ProviderLifecycleCoordinator} backed by Paper's entity scheduler and host
   * plugin audit logger if available.
   *
   * @param registry the custom screen registry
   * @param screenManager the screen manager
   * @param hostPlugin the CommandPrompter plugin instance
   */
  public ProviderLifecycleCoordinator(
      CustomScreenRegistry registry, ScreenManager screenManager, Plugin hostPlugin) {
    this(
        registry,
        screenManager,
        (hostPlugin instanceof CommandPrompter cp && cp.getCustomScreenAuditLogger() != null)
            ? cp.getCustomScreenAuditLogger()
            : CustomScreenAuditLogger.noop(),
        player -> PlayerExecutor.forPlayer(hostPlugin, player));
  }

  /**
   * Constructs a {@link ProviderLifecycleCoordinator} with an explicit audit logger backed by
   * Paper's entity scheduler.
   *
   * @param registry the custom screen registry
   * @param screenManager the screen manager
   * @param hostPlugin the CommandPrompter plugin instance
   * @param auditLogger consumer for lifecycle audit events
   */
  public ProviderLifecycleCoordinator(
      CustomScreenRegistry registry,
      ScreenManager screenManager,
      Plugin hostPlugin,
      CustomScreenAuditLogger auditLogger) {
    this(
        registry,
        screenManager,
        auditLogger,
        player -> PlayerExecutor.forPlayer(hostPlugin, player));
  }

  /**
   * Handles provider teardown when an owner plugin is disabled.
   *
   * <p>Idempotent: if the plugin has no active registrations, this is a safe no-op. When active
   * registrations exist:
   *
   * <ol>
   *   <li>Atomically unregisters and detaches all screen factories for the provider.
   *   <li>Snapshots cached provider IDs, keys, owner names, and all active player UUIDs owned by
   *       the provider.
   *   <li>Schedules teardown tasks on each active player's entity scheduler.
   *   <li>On player scheduler: re-checks active handle ownership, cancels session with {@link
   *       CancelReason#MANUAL}, unlinks state, calls {@link CustomScreenAdapter#teardownDetach()},
   *       and makes a best-effort {@link Player#closeInventory()} attempt for online players whose
   *       entity scheduler accepts work. It does NOT guarantee closure of Paper dialogs, signs,
   *       protocol UIs, arbitrary non-inventory UI, offline players, or retired schedulers.
   *       Providers own non-inventory/critical external cleanup and must not depend on {@link
   *       InputScreen#close()} during disable.
   *   <li>If player scheduler is retired, performs map-only discard without invoking provider or
   *       player APIs.
   * </ol>
   *
   * @param ownerPlugin the plugin being disabled
   */
  public void onProviderDisable(Plugin ownerPlugin) {
    if (ownerPlugin == null) {
      return;
    }

    List<CustomScreenRegistration> unregistered = registry.unregisterScreensAndGet(ownerPlugin);
    if (unregistered.isEmpty()) {
      return;
    }

    Set<Long> providerIds = new HashSet<>();
    Set<UUID> affectedPlayerUuids = new HashSet<>();

    for (CustomScreenRegistration reg : unregistered) {
      providerIds.add(reg.providerId());
      Set<UUID> playerUuids = screenManager.getActivePlayersForProvider(reg.providerId());
      if (playerUuids != null && !playerUuids.isEmpty()) {
        affectedPlayerUuids.addAll(playerUuids);
      }
    }

    String safeOwnerName = CustomScreenAuditLogger.sanitize(ownerPlugin.getName());
    auditLogger.onAuditEvent(
        new CustomScreenAuditEvent(
            CustomScreenAuditEvent.Type.TEARDOWN,
            "",
            safeOwnerName,
            -1L,
            "Teardown started: "
                + unregistered.size()
                + " screen(s), "
                + affectedPlayerUuids.size()
                + " active player(s)"));

    for (UUID playerUuid : affectedPlayerUuids) {
      Player player = Bukkit.getPlayer(playerUuid);
      if (player == null) {
        screenManager.discardState(playerUuid);
        continue;
      }

      try {
        PlayerExecutor executor = playerExecutorFactory.apply(player);
        executor.execute(
            () -> {
              if (!player.isOnline() || !playerUuid.equals(player.getUniqueId())) {
                screenManager.discardState(playerUuid);
                return;
              }
              teardownPlayerSession(player, providerIds);
            },
            () -> {
              screenManager.discardState(playerUuid);
            });
      } catch (Throwable t) {
        screenManager.discardState(playerUuid);
      }
    }
  }

  private void teardownPlayerSession(Player player, Set<Long> providerIds) {
    ActiveScreenHandle activeHandle = screenManager.getActiveScreenHandle(player.getUniqueId());
    if (activeHandle == null) {
      return;
    }

    Long providerId = activeHandle.providerId();
    if (providerId == null || !providerIds.contains(providerId)) {
      return;
    }

    screenManager.teardownCustomProvider(player, activeHandle);
  }
}
