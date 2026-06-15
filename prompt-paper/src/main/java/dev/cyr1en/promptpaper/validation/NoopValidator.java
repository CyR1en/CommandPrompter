package dev.cyr1en.promptpaper.validation;

import org.bukkit.entity.Player;

/** Always-pass validator used as a placeholder when no real validation is needed. */
public class NoopValidator implements InputValidator {
    @Override
    public boolean validate(String input) { return true; }

    @Override
    public String alias() { return "noop"; }

    @Override
    public String messageOnFail() { return ""; }

    @Override
    public Player inputPlayer() { return null; }
}
