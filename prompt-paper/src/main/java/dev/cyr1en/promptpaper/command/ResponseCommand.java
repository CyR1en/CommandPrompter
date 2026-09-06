package dev.cyr1en.promptpaper.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.approval.ApprovalCapabilityRegistry;
import dev.cyr1en.promptpaper.execution.dispatch.DispatchSanitizer;
import dev.cyr1en.promptpaper.screen.confirmation.ConsumeResult;
import dev.cyr1en.promptpaper.screen.confirmation.NonceResponseRegistry;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import org.bukkit.entity.Player;

/**
 * Hidden internal response command: {@code /commandprompter:response <nonce> <confirm|decline>}.
 *
 * <p>Player-only endpoint for interactive chat confirmation and approval gate clicks. Applies a
 * sliding-window rate limiter per player UUID before performing cryptographic nonce lookup.
 */
public class ResponseCommand extends PromptCommand implements Command<CommandSourceStack> {

  public ResponseCommand(CommandPrompter plugin) {
    super(
        plugin,
        "commandprompter:response",
        null,
        Player.class,
        "Internal confirmation prompt response receiver",
        List.of());
  }

  @Override
  public LiteralCommandNode<CommandSourceStack> build() {
    return Commands.literal(name())
        .requires(src -> allowed(src.getSender()))
        .then(
            Commands.argument("nonce", StringArgumentType.word())
                .then(Commands.argument("decision", StringArgumentType.word()).executes(this)))
        .build();
  }

  @Override
  public int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
    var sender = context.getSource().getSender();
    if (!(sender instanceof Player player)) {
      return Command.SINGLE_SUCCESS;
    }

    var nonce = StringArgumentType.getString(context, "nonce");
    var decision = StringArgumentType.getString(context, "decision");
    executeResponse(player, nonce, decision);
    return Command.SINGLE_SUCCESS;
  }

  public void executeResponse(Player player, String nonce, String decision) {
    var uuid = player.getUniqueId();
    var rateLimiter = plugin.getRateLimiter();
    if (rateLimiter != null && !rateLimiter.tryAcquire(uuid)) {
      if (rateLimiter.shouldLogRateLimit(uuid)) {
        plugin
            .getPluginLogger()
            .warn(
                "Rate limited confirmation response attempts for "
                    + player.getName()
                    + " (exceeded "
                    + rateLimiter.maxAttempts()
                    + " attempts in "
                    + rateLimiter.window().toSeconds()
                    + "s)");
      }
      return;
    }

    // 1. If nonce is classified as approval capability (namespaced with a_), permanently handle via
    // ApprovalCoordinator. It MUST NEVER fall through to local confirmation session!
    if (ApprovalCapabilityRegistry.isApprovalNonce(nonce)) {
      var approvalCoordinator = plugin.getApprovalCoordinator();
      if (approvalCoordinator != null) {
        approvalCoordinator.handleResponse(player, nonce, decision);
      }
      return;
    }

    // 2. Non-approval nonces route to local confirmation PromptSession path
    player
        .getScheduler()
        .run(
            plugin,
            task -> {
              var sessionOpt = plugin.getEngine().getSession(player);
              if (sessionOpt.isEmpty() || !sessionOpt.get().isActive()) {
                if (rateLimiter == null || rateLimiter.shouldLogRejection(uuid)) {
                  int attempts = rateLimiter != null ? rateLimiter.attemptCount(uuid) : 1;
                  plugin
                      .getPluginLogger()
                      .warn(
                          "Confirmation response rejected: no active session for "
                              + player.getName()
                              + " [nonce="
                              + NonceResponseRegistry.truncateNonce(nonce)
                              + ", attempts="
                              + attempts
                              + "]");
                }
                return;
              }

              var session = sessionOpt.get();
              var registry = plugin.getNonceRegistry();
              if (registry == null) {
                return;
              }

              var consumeResult =
                  registry.consume(
                      nonce,
                      uuid,
                      session.incarnation(),
                      session.generation(),
                      session.currentIndex(),
                      decision);

              switch (consumeResult) {
                case ConsumeResult.Success(var binding, var acceptedDecision) -> {
                  if (binding.callback() != null) {
                    binding.callback().accept(acceptedDecision);
                  }
                }
                case ConsumeResult.Rejected(var reason) -> {
                  if (rateLimiter == null || rateLimiter.shouldLogRejection(uuid)) {
                    int attempts = rateLimiter != null ? rateLimiter.attemptCount(uuid) : 1;
                    String safeReason = DispatchSanitizer.sanitizeDetail(reason.name());
                    plugin
                        .getPluginLogger()
                        .warn(
                            "Confirmation response rejected for "
                                + player.getName()
                                + ": "
                                + safeReason
                                + " [nonce="
                                + NonceResponseRegistry.truncateNonce(nonce)
                                + ", attempts="
                                + attempts
                                + "]");
                  }
                }
                case null -> {}
              }
            },
            () -> {
              plugin.getScreenManager().discardState(uuid);
            });
  }
}
