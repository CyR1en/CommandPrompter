package com.cyr1en.commandprompter.prompt.validators;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.PluginLogger;
import com.cyr1en.commandprompter.api.prompt.CompoundableValidator;
import com.cyr1en.commandprompter.api.prompt.InputValidator;
import com.cyr1en.commandprompter.hook.hooks.PapiHook;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;

import javax.script.ScriptEngineManager;

public class JSExprValidator implements InputValidator, CompoundableValidator {

    public static final CompoundableValidator.Type DEFAULT_TYPE = Type.AND;
    private final String alias;
    private final String expression;
    private final String messageOnFail;
    private final Player inputPlayer;
    private final PluginLogger logger;
    private ScriptEngineManager engine;

    private CompoundableValidator.Type type = DEFAULT_TYPE;

    public JSExprValidator(String alias, String expression, String messageOnFail, Player inputPlayer) {
        this.alias = alias;
        this.expression = expression;
        this.messageOnFail = messageOnFail;
        this.inputPlayer = inputPlayer;
        this.logger = CommandPrompter.getInstance().getPluginLogger();
        initEngine();
    }

    private void initEngine() {
        var manager = Bukkit.getServer().getServicesManager();
        var factory = new NashornScriptEngineFactory();
        if (engine == null) {
            if (manager.isProvidedFor(ScriptEngineManager.class)) {
                final RegisteredServiceProvider<ScriptEngineManager> provider = manager.getRegistration(ScriptEngineManager.class);
                if (provider == null) {
                    logger.debug("ScriptEngineManager provider is null");
                    return;
                }
                engine = provider.getProvider();
            } else {
                engine = new ScriptEngineManager();
                manager.register(ScriptEngineManager.class, engine, CommandPrompter.getInstance(), ServicePriority.Highest);
            }
            engine.registerEngineName("JavaScript", factory);
            engine.put("BukkitServer", Bukkit.getServer());
        }
    }

    @Override
    public boolean validate(String input) {
        var exprStr = expression.replace("%prompt_input%", input);

        var hook = CommandPrompter.getInstance().getHookContainer().getHook(PapiHook.class);
        if (hook.isHooked())
            exprStr = hook.get().setPlaceholder(inputPlayer, exprStr);

        logger.debug("JS expression: " + exprStr);
        if (exprStr.isBlank()) {
            logger.debug("JS expression is blank");
            return false;
        }

        return evaluate(exprStr);
    }

    private boolean evaluate(String exprStr) {
        try {
            engine.put("BukkitPlayer", inputPlayer);
            logger.debug("Evaluating JS expression: " + exprStr);
            var result = engine.getEngineByName("JavaScript").eval(exprStr);
            if (result instanceof Boolean) {
                return (Boolean) result;
            } else {
                logger.debug("JS expression did not return a boolean");
                return false;
            }
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
