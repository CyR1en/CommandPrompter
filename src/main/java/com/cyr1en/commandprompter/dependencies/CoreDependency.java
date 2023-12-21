package com.cyr1en.commandprompter.dependencies;

import com.cyr1en.commandprompter.PluginLogger;

import java.io.File;

/**
 * Represents a core dependency for the plugin.
 * This dependency is required for the plugin to work.
 * 
 * <p>
 * These type of dependencies must be available in the classpath by the time the
 * plugin is loaded.
 * if they're not present in the classpath, the plugin will try to download them
 * and attempt to load them.
 * 
 * <p>
 * These should not have any relocations and must stay as is.
 */
public enum CoreDependency {
    RELOCATOR(
            "jar-relocator-1.7.jar",
            "https://repo1.maven.org/maven2/me/lucko/jar-relocator/1.7/jar-relocator-1.7.jar",
            "1584ce507e0c165e219d32b33765d42988494891",
            "me.lucko.jarrelocator.JarRelocator"),
    ASM_COMMONS(
            "asm-commons-9.5.jar",
            "https://repo1.maven.org/maven2/org/ow2/asm/asm-commons/9.5/asm-commons-9.5.jar",
            "19ab5b5800a3910d30d3a3e64fdb00fd0cb42de0",
            "org.objectweb.asm.commons.Remapper"),
    GSON(
            "gson-2.10.1.jar",
            "https://repo1.maven.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar",
            "b3add478d4382b78ea20b1671390a858002feb6c",
            "com.google.gson.JsonParser");

    private final String fileName;
    private final String url;
    private final String sha1;
    private final String targetClass;

    private static PluginLogger logger;

    CoreDependency(String filename, String url, String sha1, String targetClass) {
        this.fileName = filename;
        this.url = url;
        this.sha1 = sha1;
        this.targetClass = targetClass;
    }

    public Dependency asDependency() {
        return new Dependency(fileName, url, new String[] { "", "" }, sha1);
    }

    public boolean inClassPath() {
        logger.debug("Checking if %s is loaded", fileName);
        try {
            Class.forName(targetClass, false, logger.getClass().getClassLoader());
            logger.debug("%s found in classpath", fileName);
            return true;
        } catch (ClassNotFoundException e) {
            logger.debug("%s not found in classpath!", fileName);
            return false;
        }
    }

    boolean load(URLClassLoaderAccess access, File libDir) {
        if (!inClassPath()) {
            logger.debug("Loading %s...", fileName);
            try {
                var file = new File(libDir, fileName);
                var url = file.toURI().toURL();
                if (url != null) {
                    access.addURL(url);
                    logger.debug("%s loaded!", fileName);
                } else {
                    sendErrorMessage();
                    return false;
                }
            } catch (Exception e) {
                sendErrorMessage();
                return false;
            }
        }
        return true;
    }

    boolean download(File libDir) {
        logger.debug("Downloading %s", fileName);
        try {
            var downloaded = asDependency().downloadChecked(libDir);
            if (!downloaded) {
                sendErrorMessage();
                return false;
            }
        } catch (Exception e) {
            sendErrorMessage();
            return false;
        }
        return true;
    }

    String getFileName() {
        return fileName;
    }

    void sendErrorMessage() {
        logger.err("Failed to download %s!", fileName);
        logger.err("Please download it manually from %s and put it in the lib folder.", url);
    }

    public static boolean loadAll(URLClassLoaderAccess access, File libDir) {
        logger.info("Loading core dependencies...");
        for (CoreDependency dependency : values()) {
            if (dependency.inClassPath())
                continue;
            var result = dependency.download(libDir);
            if (!result)
                return false;
            result = dependency.load(access, libDir);
            if (!result)
                return false;
        }
        logger.info("Finished loading core dependencies!");
        return true;
    }

    public static void initLogger(PluginLogger logger) {
        CoreDependency.logger = logger;
    }
}