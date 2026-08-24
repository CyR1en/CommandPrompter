package dev.cyr1en.promptui.api;

import dev.cyr1en.promptui.InputScreen;
import org.bukkit.entity.Player;

/**
 * Factory interface for instantiating custom interactive {@link InputScreen} instances.
 *
 * <p>Registered with {@link CommandPrompterAPI#registerScreen(org.bukkit.plugin.Plugin, String, PromptScreenFactory)},
 * this factory is invoked whenever CommandPrompter encounters a prompt tag matching the registered key.
 *
 * <h2>Threading &amp; Concurrency Contract</h2>
 *
 * <ul>
 *   <li><b>Player Entity Scheduler Execution:</b> On Paper and Folia servers, {@link #createScreen(Player, ScreenContext)}
 *       is executed synchronously on the target player's entity scheduler (or region thread on Folia / main thread on Paper).</li>
 *   <li><b>Non-Blocking:</b> Implementations <b>MUST NOT</b> block the calling thread. Long-running operations
 *       (database calls, web requests, blocking I/O) must never be executed synchronously inside this method.
 *       Factory invocation must return promptly.</li>
 *   <li><b>Callback Threading &amp; Exactly-Once Protection:</b> The callback registered via {@link InputScreen#onResult(java.util.function.Consumer)}
 *       may be invoked from any thread context. CommandPrompter's internal adapter automatically hops the result onto
 *       the player's entity scheduler, verifies session generation tokens, and enforces exactly-once completion. Providers
 *       should still avoid emitting duplicate callbacks.</li>
 *   <li><b>Lifecycle Fulfillment:</b> The returned {@link InputScreen} must adhere to the standard lifecycle:
 *       <ol>
 *         <li>Register a result callback when CommandPrompter calls {@link InputScreen#onResult(java.util.function.Consumer)}.</li>
 *         <li>Optionally register a failure callback when CommandPrompter calls {@link InputScreen#onOpenFailure(java.util.function.Consumer)}.</li>
 *         <li>Present the UI when CommandPrompter calls {@link InputScreen#open()}.</li>
 *         <li>Deliver exactly one {@link dev.cyr1en.promptui.ScreenResult} to the callback upon player submission or cancellation.</li>
 *       </ol>
 *   </li>
 *   <li><b>Error Boundary:</b> Any unhandled exception thrown during {@code createScreen}, {@code open()},
 *       or within provider callbacks is intercepted by CommandPrompter's internal adapter and safely
 *       cancels the prompt session with {@link dev.cyr1en.promptcore.CancelReason#ERROR}, protecting the server.</li>
 * </ul>
 *
 * @since 3.3.0
 */
@FunctionalInterface
public interface PromptScreenFactory {

  /**
   * Instantiates an {@link InputScreen} for the target player and prompt context.
   *
   * @param player the target player receiving the prompt (non-null)
   * @param context the immutable prompt context containing canonical key, display text, flags, and sanitize mode (non-null)
   * @return a newly created, un-opened {@link InputScreen} instance ready for lifecycle binding (non-null)
   */
  InputScreen createScreen(Player player, ScreenContext context);
}
