package dev.cyr1en.promptpaper.validation;

import java.util.ArrayList;
import org.bukkit.entity.Player;

/**
 * Meta-validator that composes multiple {@link InputValidator} instances.
 * Non-{@link CompoundableValidator} entries default to AND semantics.
 * {@link CompoundableValidator#Type#AND} entries must all pass;
 * {@link CompoundableValidator#Type#OR} entries require at least one pass.
 * Both groups must succeed for the compound to pass.
 */
public class CompoundedValidator implements InputValidator {

    private final String alias;
    private final String messageOnFail;
    private final Player inputPlayer;
    private final InputValidator[] validators;

    public CompoundedValidator(String alias, String messageOnFail, Player inputPlayer,
                               InputValidator... validators) {
        this.alias = alias;
        this.messageOnFail = messageOnFail;
        this.inputPlayer = inputPlayer;
        this.validators = validators;
    }

    /**
     * Partitions validators into AND and OR groups, evaluates each group,
     * and returns {@code true} only if both groups pass.
     */
    @Override
    public boolean validate(String input) {
        if (input == null || input.isBlank()) return false;
        var ands = new ArrayList<InputValidator>();
        var ors = new ArrayList<InputValidator>();
        for (var v : validators) {
            if (v instanceof CompoundableValidator cv) {
                if (cv.getType() == CompoundableValidator.Type.OR)
                    ors.add(v);
                else
                    ands.add(v);
            } else {
                ands.add(v);
            }
        }
        var andPass = ands.isEmpty() || ands.stream().allMatch(v -> v.validate(input));
        var orPass = ors.isEmpty() || ors.stream().anyMatch(v -> v.validate(input));
        return andPass && orPass;
    }

    @Override
    public String alias() { return alias; }

    @Override
    public String messageOnFail() { return messageOnFail; }

    @Override
    public Player inputPlayer() { return inputPlayer; }
}
