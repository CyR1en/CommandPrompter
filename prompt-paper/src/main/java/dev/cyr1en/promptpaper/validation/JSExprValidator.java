package dev.cyr1en.promptpaper.validation;

import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.hook.hooks.PapiHook;
import dev.cyr1en.promptpaper.util.PluginLogger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.script.ScriptEngine;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;
import org.openjdk.nashorn.api.tree.LiteralTree;
import org.openjdk.nashorn.api.tree.Parser;
import org.openjdk.nashorn.api.tree.SimpleTreeVisitorES6;

/**
 * Validates user input by evaluating a JavaScript expression via Nashorn. The expression may
 * reference {@code %prompt_input%} (bound as data), {@code BukkitServer}, and {@code BukkitPlayer}.
 * PlaceholderAPI placeholders are resolved when the PapiHook is active.
 */
public class JSExprValidator implements InputValidator, CompoundableValidator {

  public static final Type DEFAULT_TYPE = Type.AND;
  private static final Pattern DECIMAL =
      Pattern.compile("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?");
  private final String inputBinding = "__cp_input_" + UUID.randomUUID().toString().replace("-", "");
  private final String alias;
  private final String expression;
  private final String messageOnFail;
  private final Player inputPlayer;
  private final CommandPrompter plugin;
  private final PluginLogger logger;
  private final ScriptEngine engine;
  private Type type = DEFAULT_TYPE;

  public JSExprValidator(
      String alias,
      String expression,
      String messageOnFail,
      Player inputPlayer,
      CommandPrompter plugin) {
    this.alias = alias;
    this.expression = expression;
    this.messageOnFail = messageOnFail;
    this.inputPlayer = inputPlayer;
    this.plugin = plugin;
    this.logger = plugin.getPluginLogger();
    this.engine = initEngine();
  }

  /** Initializes the Nashorn script engine with a {@code BukkitServer} binding. */
  private ScriptEngine initEngine() {
    var factory = new NashornScriptEngineFactory();
    var eng = factory.getScriptEngine();
    eng.put("BukkitServer", Bukkit.getServer());
    return eng;
  }

  /**
   * Evaluates the JavaScript expression with the current input substituted in, after resolving any
   * PlaceholderAPI placeholders. Returns {@code true} only if the expression evaluates to a {@code
   * Boolean} {@code true}.
   */
  @Override
  public synchronized boolean validate(String input) {
    if (engine == null || input == null) return false;

    final var player = inputPlayer;
    final var initialExpr = expression.replace("%prompt_input%", inputBinding);
    var exprStr =
        plugin
            .getHookContainer()
            .getHook(PapiHook.class)
            .filter(h -> player != null)
            .map(h -> h.setPlaceholder(player, initialExpr))
            .orElse(initialExpr);

    logger.debug("JS expression: " + exprStr);
    if (exprStr.isBlank()) {
      logger.debug("JS expression is blank");
      return false;
    }

    return evaluate(exprStr, input);
  }

  /**
   * Runs the JS expression in the Nashorn engine and returns whether it produced a truthy boolean
   * result.
   */
  // Server admin configured JS expression evaluation is architectural intent.
  // nosemgrep: java.lang.security.audit.script-engine-injection

  private boolean evaluate(String exprStr, String input) {
    try {
      var bindings = engine.createBindings();
      bindings.put("BukkitServer", Bukkit.getServer());
      if (inputPlayer != null) bindings.put("BukkitPlayer", inputPlayer);
      bindings.put(
          inputBinding,
          DECIMAL.matcher(input.trim()).matches() ? Double.valueOf(input.trim()) : input);

      // Nashorn identifies string literals so quotes, escapes, comments, and regex literals
      // cannot trick input substitution into becoming executable JavaScript.
      var literals = new ArrayList<LiteralTree>();
      Parser.create()
          .parse(alias, exprStr, null)
          .accept(
              new SimpleTreeVisitorES6<Void, Void>() {
                @Override
                public Void visitLiteral(LiteralTree literal, Void unused) {
                  if (literal.getValue() instanceof String value && value.contains(inputBinding))
                    literals.add(literal);
                  return null;
                }
              },
              null);
      literals.sort(Comparator.comparingLong(LiteralTree::getStartPosition).reversed());
      var source = new StringBuilder(exprStr);
      for (int i = 0; i < literals.size(); i++) {
        var literal = literals.get(i);
        var binding = inputBinding + "_literal_" + i;
        bindings.put(binding, ((String) literal.getValue()).replace(inputBinding, input));
        // Nashorn's string-literal offsets exclude the surrounding quotes.
        source.replace(
            (int) literal.getStartPosition() - 1, (int) literal.getEndPosition() + 1, binding);
      }
      logger.debug("Evaluating JS expression: " + exprStr);
      // nosemgrep: java.lang.security.audit.script-engine-injection

      var result = engine.eval(source.toString(), bindings);
      if (result instanceof Boolean b) return b;
      logger.debug("JS expression did not return a boolean");
      return false;
    } catch (Exception e) {
      logger.debug("JS expression failed to evaluate: " + e.getMessage());
      return false;
    }
  }

  @Override
  public String alias() {
    return alias;
  }

  @Override
  public String messageOnFail() {
    return messageOnFail;
  }

  @Override
  public Player inputPlayer() {
    return inputPlayer;
  }

  @Override
  public Type getType() {
    return type;
  }

  @Override
  public void setType(Type type) {
    this.type = type;
  }
}
