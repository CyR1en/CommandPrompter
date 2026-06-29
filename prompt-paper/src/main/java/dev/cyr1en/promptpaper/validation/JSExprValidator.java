package dev.cyr1en.promptpaper.validation;

import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.hook.hooks.PapiHook;
import dev.cyr1en.promptpaper.util.PluginLogger;
import javax.script.ScriptEngine;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;

/**
 * Validates user input by evaluating a JavaScript expression via Nashorn.
 * The expression may reference {@code %prompt_input%} (replaced with the input),
 * {@code BukkitServer}, and {@code BukkitPlayer}. PlaceholderAPI placeholders
 * are resolved when the PapiHook is active.
 */
public class JSExprValidator implements InputValidator, CompoundableValidator {

    public static final Type DEFAULT_TYPE = Type.AND;
    private final String alias;
    private final String expression;
    private final String messageOnFail;
    private final Player inputPlayer;
    private final CommandPrompter plugin;
    private final PluginLogger logger;
    private final ScriptEngine engine;
    private Type type = DEFAULT_TYPE;

    public JSExprValidator(String alias, String expression, String messageOnFail, Player inputPlayer, CommandPrompter plugin) {
        this.alias = alias;
        this.expression = expression;
        this.messageOnFail = messageOnFail;
        this.inputPlayer = inputPlayer;
        this.plugin = plugin;
        this.logger = plugin.getPluginLogger();
        this.engine = initEngine();
    }

    /**
     * Initializes the Nashorn script engine with a {@code BukkitServer} binding.
     */
    private ScriptEngine initEngine() {
        var factory = new NashornScriptEngineFactory();
        var eng = factory.getScriptEngine();
        eng.put("BukkitServer", Bukkit.getServer());
        return eng;
    }

    /**
     * Evaluates the JavaScript expression with the current input substituted in,
     * after resolving any PlaceholderAPI placeholders. Returns {@code true} only
     * if the expression evaluates to a {@code Boolean} {@code true}.
     */
    @Override
    public boolean validate(String input) {
        if (engine == null) return false;

        final var player = inputPlayer;
        final var initialExpr = expression.replace("%prompt_input%", input);
        var exprStr = plugin.getHookContainer().getHook(PapiHook.class)
                .filter(h -> player != null)
                .map(h -> h.setPlaceholder(player, initialExpr))
                .orElse(initialExpr);

        logger.debug("JS expression: " + exprStr);
        if (exprStr.isBlank()) {
            logger.debug("JS expression is blank");
            return false;
        }

        return evaluate(exprStr);
    }

    /**
     * Runs the JS expression in the Nashorn engine and returns whether it
     * produced a truthy boolean result.
     */
    // Server admin configured JS expression evaluation is architectural intent.
    // nosemgrep: java.lang.security.audit.script-engine-injection
    // nosemgrep
    private boolean evaluate(String exprStr) {
        try {
            if (inputPlayer != null)
                engine.put("BukkitPlayer", inputPlayer);
            logger.debug("Evaluating JS expression: " + exprStr);
            // nosemgrep: java.lang.security.audit.script-engine-injection
            // nosemgrep
            var result = engine.eval(exprStr);
            if (result instanceof Boolean b)
                return b;
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
