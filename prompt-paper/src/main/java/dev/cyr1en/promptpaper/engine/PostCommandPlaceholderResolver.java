package dev.cyr1en.promptpaper.engine;

import dev.cyr1en.promptpaper.hook.HookContainer;
import dev.cyr1en.promptpaper.hook.hooks.PapiHook;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.entity.Player;

/**
 * Resolves session-scoped placeholders in post-command templates.
 *
 * <h2>Supported placeholders</h2>
 *
 * <ul>
 *   <li>{@code {player}} — the player who triggered the prompt flow (executor).
 *   <li>{@code {input}} / {@code {input:1}} — the first completed prompt's answer
 *       (1-indexed, per the spec).
 *   <li>{@code {input:N}} — the Nth completed prompt's answer (N &ge; 1).
 *   <li>{@code %…%} — PlaceholderAPI placeholders, resolved against the player when
 *       PAPI is installed.
 * </ul>
 *
 * <p>Unresolved {@code {input:N}} references (N out of range) are left in place
 * rather than stripped, so the operator can see them in the dispatched command
 * and debug missing answers.
 */
public class PostCommandPlaceholderResolver {

  /**
   * Matches {@code {input}} or {@code {input:N}} (N = decimal digits). The first
   * capture group is the index digits, or {@code null} for the bare {@code {input}}.
   */
  private static final Pattern INPUT_PATTERN = Pattern.compile("\\{input(?::(\\d+))?\\}");

  private final HookContainer hookContainer;

  public PostCommandPlaceholderResolver(HookContainer hookContainer) {
    this.hookContainer = hookContainer;
  }

  /**
   * Resolves every supported placeholder in {@code template} for the given player
   * and answer list.
   *
   * @param template the post-command template (may be {@code null} or empty)
   * @param player the player who owns this post-command
   * @param answers the raw collected answers from the session, in prompt order
   * @return the resolved string, or the original template if no placeholders are present
   */
  public String resolve(String template, Player player, List<String> answers) {
    if (template == null || template.isEmpty()) return template;
    var result = template;
    result = result.replace("{player}", player.getName());
    result = resolveInputs(result, answers);
    result = resolvePapi(result, player);
    return result;
  }

  private String resolveInputs(String template, List<String> answers) {
    if (template.indexOf("{input") < 0) return template; // fast path
    var matcher = INPUT_PATTERN.matcher(template);
    if (!matcher.find()) return template;
    matcher.reset();
    var sb = new StringBuilder();
    while (matcher.find()) {
      var digits = matcher.group(1);
      int idx = (digits == null) ? 1 : Integer.parseInt(digits);
      String replacement;
      if (idx >= 1 && idx <= answers.size()) {
        replacement = answers.get(idx - 1);
      } else {
        // Leave the unresolved placeholder in place so the operator can see
        // that the answer index is out of range.
        replacement = matcher.group();
      }
      matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(sb);
    return sb.toString();
  }

  private String resolvePapi(String template, Player player) {
    if (template.indexOf('%') < 0) return template; // fast path
    var papi = hookContainer.getHook(PapiHook.class);
    if (papi.isEmpty()) return template;
    return papi.get().setPlaceholder(player, template);
  }
}
