package com.cyr1en.commandprompter.util;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.text.MessageFormat;

public class FormatUtil {
    public static String format(@NonNull String template, @Nullable Object... args) {
        return MessageFormat.format(template, args);
    }
}
