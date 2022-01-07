package com.cyr1en.commandprompter.util;

import org.bukkit.ChatColor;
import org.bukkit.Material;

import java.util.Locale;
import java.util.Objects;

public class Util {
    public static String stripColor(String msg) {
        return ChatColor.stripColor(color(msg));
    }

    public static String color(String msg) {
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    public static Material getCheckedMaterial(String materialString, Material defaultMaterial) {
        materialString = materialString.toUpperCase(Locale.ROOT);
        var mat = Material.getMaterial(materialString);
        return Objects.isNull(mat) ? defaultMaterial : mat;
    }
}
