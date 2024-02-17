package com.cyr1en.commandprompter.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;

import java.util.List;

public class MMUtil {

    public static List<String> filterOutMiniMessageTags(List<String> strs) {
        return strs.stream().filter(str -> !isMiniMessageTag(str)).toList();
    }

    /**
     * Function that checks if the prompt is a mini message tag.
     *
     * @param str Prompt to check
     * @return true if the prompt is a mini message tag, false otherwise.
     */
    public static boolean isMiniMessageTag(String str) {
        var serializer = MiniMessage.builder()
                .tags(StandardTags.defaults()).build();
        var parsed = serializer.deserialize(str);
        return !Component.text(str).equals(parsed);
    }

}
