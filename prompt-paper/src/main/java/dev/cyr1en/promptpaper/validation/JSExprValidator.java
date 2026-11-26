package dev.cyr1en.promptpaper.validation;

import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.hook.hooks.PapiHook;
import dev.cyr1en.promptpaper.util.PluginLogger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
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
 * PlaceholderAPI placeholders are resolved one by one when the PapiHook is active and are bound as
 * data as well. Scripts cannot load Java classes.
 */
public class JSExprValidator implements InputValidator, CompoundableValidator {

  public static final Type DEFAULT_TYPE = Type.AND;
  private static final Pattern DECIMAL =
      Pattern.compile("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?");
  private final String bindingPrefix =
      "__cp_" + UUID.randomUUID().toString().replace("-", "") + "_";
  private final String inputBinding = bindingPrefix + "input";
  private final Pattern bindingName = Pattern.compile(bindingPrefix + "(?:input|p\\d+_)");
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

  /**
   * Initializes the Nashorn script engine with a {@code BukkitServer} binding. The deny-all class
   * filter keeps scripts from loading Java classes (e.g. via {@code Java.type}).
   */
  private ScriptEngine initEngine() {
    var factory = new NashornScriptEngineFactory();
    var eng = factory.getScriptEngine(className -> false);
    eng.put("BukkitServer", Bukkit.getServer());
    return eng;
  }

  /**
   * Evaluates the JavaScript expression. The input and every resolved PlaceholderAPI placeholder
   * are bound as script variables, never pasted into the source. Returns {@code true} only if the
   * expression evaluates to a {@code Boolean} {@code true}.
   */
  @Override
  public synchronized boolean validate(String input) {
    if (engine == null || input == null) return false;

    var values = new HashMap<String, String>();
    values.put(inputBinding, input);
    final var player = inputPlayer;
    final var initialExpr = expression.replace("%prompt_input%", inputBinding);
    var exprStr =
        plugin
            .getHookContainer()
            .getHook(PapiHook.class)
            .filter(h -> player != null)
            .map(h -> bindPlaceholders(h, player, initialExpr, values))
            .orElse(initialExpr);

    logger.debug("JS expression: " + exprStr);
    if (exprStr.isBlank()) {
      logger.debug("JS expression is blank");
      return false;
    }

    return evaluate(exprStr, values);
  }

  /**
   * Replaces each {@code %...%} token that PlaceholderAPI resolves with a unique binding name and
   * records its value. Tokens are resolved one at a time so a value can never reach the source.
   */
  private String bindPlaceholders(
      PapiHook papi, Player player, String expr, Map<String, String> values) {
    var out = new StringBuilder();
    int i = 0;
    while (i < expr.length()) {
      int end = expr.charAt(i) == '%' ? expr.indexOf('%', i + 1) : -1;
      if (end > i + 1) {
        var token = expr.substring(i, end + 1);
        var value = papi.setPlaceholder(player, token);
        if (value != null && !value.equals(token)) {
          var binding = bindingPrefix + "p" + values.size() + "_";
          values.put(binding, value);
          out.append(binding);
          i = end + 1;
          continue;
        }
      }
      out.append(expr.charAt(i++));
    }
    return out.toString();
  }

  private static Object typed(String value) {
    var trimmed = value.trim();
    return DECIMAL.matcher(trimmed).matches() ? (Object) Double.valueOf(trimmed) : value;
  }

  /**
   * Runs the JS expression in the Nashorn engine and returns whether it produced a truthy boolean
   * result.
   */
  // Server admin configured JS expression evaluation is architectural intent.
  // nosemgrep: java.lang.security.audit.script-engine-injection

  private boolean evaluate(String exprStr, Map<String, String> values) {
    try {
      var bindings = engine.createBindings();
      bindings.put("BukkitServer", Bukkit.getServer());
      if (inputPlayer != null) bindings.put("BukkitPlayer", inputPlayer);
      values.forEach((name, value) -> bindings.put(name, typed(value)));

      // Nashorn identifies string literals so quotes, escapes, comments, and regex literals
      // cannot trick value substitution into becoming executable JavaScript.
      var literals = new ArrayList<LiteralTree>();
      Parser.create()
          .parse(alias, exprStr, null)
          .accept(
              new SimpleTreeVisitorES6<Void, Void>() {
                @Override
                public Void visitLiteral(LiteralTree literal, Void unused) {
                  if (literal.getValue() instanceof String value && value.contains(bindingPrefix))
                    literals.add(literal);
                  return null;
                }
              },
              null);
      literals.sort(Comparator.comparingLong(LiteralTree::getStartPosition).reversed());
      var source = new StringBuilder(exprStr);
      for (int i = 0; i < literals.size(); i++) {
        var literal = literals.get(i);
        var binding = bindingPrefix + "literal_" + i;
        bindings.put(
            binding,
            bindingName
                .matcher((String) literal.getValue())
                .replaceAll(
                    m -> Matcher.quoteReplacement(values.getOrDefault(m.group(), m.group()))));
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
