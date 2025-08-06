package com.cyr1en.commandprompter.common.util;


import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.text.MessageFormat;

public class Util {

    public static String format(@NotNull String template, @Nullable Object... args) {
        return MessageFormat.format(template, args);
    }

}
