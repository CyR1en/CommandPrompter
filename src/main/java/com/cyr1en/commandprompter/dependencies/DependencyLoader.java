package com.cyr1en.commandprompter.dependencies;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.util.Util;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import me.lucko.jarrelocator.JarRelocator;
import me.lucko.jarrelocator.Relocation;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

public class DependencyLoader {

    private static final Dependency RELOCATOR_JAR = new Dependency(
            "jar-relocator-1.7.jar",
            "https://repo1.maven.org/maven2/me/lucko/jar-relocator/1.7/jar-relocator-1.7.jar",
            new String[]{"", ""},
            "1584ce507e0c165e219d32b33765d42988494891");

    private static final Dependency ASM_COMMONS = new Dependency(
            "asm-commons-9.5.jar",
            "https://repo1.maven.org/maven2/org/ow2/asm/asm-commons/9.5/asm-commons-9.5.jar",
            new String[]{"", ""},
            "19ab5b5800a3910d30d3a3e64fdb00fd0cb42de0");

    private final URLClassLoaderAccess access;
    private final CommandPrompter plugin;
    private final File libDir;

    private final boolean relocatorAvailable;

    public DependencyLoader(CommandPrompter plugin) {
        this.plugin = plugin;
        access = URLClassLoaderAccess.create((URLClassLoader) plugin.getClass().getClassLoader());
        libDir = initLibDirectory();
        relocatorAvailable = initRelocator();
    }

    public boolean isRelocatorAvailable() {
        return relocatorAvailable;
    }

    private boolean initRelocator() {
        initASM();
        plugin.getPluginLogger().debug("Loading relocator jar...");
        try {
            if (!downloadCheckedDep(RELOCATOR_JAR)) return false;

            var file = new File(libDir, RELOCATOR_JAR.filename());
            access.addURL(file.toURI().toURL());
            plugin.getPluginLogger().debug("Relocator jar loaded!");
            return true;
        } catch (IOException e) {
            plugin.getPluginLogger().err("Failed to download relocator jar!");
            plugin.getPluginLogger().err("Cannot load dependencies on run time!");
            plugin.getPluginLogger().err("Please download it manually from " + RELOCATOR_JAR.url() + " and put it in the lib folder.");
            sendBundledMessage();
            return false;
        }
    }

    private void initASM() {
        try {
            plugin.getPluginLogger().debug("Checking ASM Remapper...");
            var clz = Class.forName("org.objectweb.asm.commons.Remapper");
            plugin.getPluginLogger().debug("ASM Remapper found!");
        } catch (ClassNotFoundException e) {
            plugin.getPluginLogger().debug("ASM Remapper not found!");
            plugin.getPluginLogger().debug("Loading ASM...");
            try {
                if (!downloadCheckedDep(ASM_COMMONS)) return;
                var file = new File(libDir, ASM_COMMONS.filename());
                access.addURL(file.toURI().toURL());
                plugin.getPluginLogger().debug("ASM loaded!");
            } catch (IOException ex) {
                plugin.getPluginLogger().err("Failed to download ASM!");
                plugin.getPluginLogger().err("Cannot load dependencies on run time!");
                plugin.getPluginLogger().err("Please download it manually from " + ASM_COMMONS.url() + " and put it in the lib folder.");
                sendBundledMessage();
            }
        }
    }

    private boolean downloadCheckedDep(Dependency dependency) throws IOException {
        var file = new File(libDir, dependency.filename());
        if (file.exists()) {
            plugin.getPluginLogger().debug(dependency.filename() + " already exists!");
            return true;
        }

        var in = new URL(dependency.url()).openStream();
        Files.copy(in, file.toPath());

        var sha1 = dependency.sha1();
        if (!sha1.isBlank() && !Util.checkSHA1(file, sha1)) {
            plugin.getPluginLogger().warn(dependency.filename() + " checksum does not match!");
            plugin.getPluginLogger().err("Please download it manually from " + dependency.url() + " and put it in the lib folder.");
            file.delete();
            return false;
        }
        return true;
    }

    public void sendBundledMessage() {
        var version = plugin.getUpdateChecker().getCurrVersion().asString();
        plugin.getPluginLogger().err("Alternatively, you can download the bundled version of CommandPrompter-" + version + " here:");
        plugin.getPluginLogger().err("https://github.com/CyR1en/CommandPrompter/releases/tag/" + version);
    }

    private void sendDownloadManually(Dependency dependency) {
        plugin.getPluginLogger().err("Please download it manually from " + dependency.url() + " and put it in the lib folder.");
    }

    public File initLibDirectory() {
        var dataFolder = plugin.getDataFolder();
        var libDir = new File(dataFolder, "lib");
        if (!libDir.exists())
            libDir.mkdir();
        return libDir;
    }

    public void loadDependency() {
        plugin.getPluginLogger().info("Loading dependencies...");
        var dependencies = readDependencies("runtime-deps.json");
        downloadDependencies(dependencies);
        loadAll(dependencies);
        plugin.getPluginLogger().info("Finished loading dependencies");
    }

    private void loadAll(ImmutableList<Dependency> dependencies) {
        for (Dependency dependency : dependencies) {
            var file = new File(libDir, dependency.filename());
            if (!file.exists()) continue;
            try {
                var relocation = dependency.relocation();
                plugin.getPluginLogger().debug("Relocation: " + Arrays.toString(relocation));
                if (relocation.length == 2 && !relocation[0].isBlank() && !relocation[1].isBlank()) {
                    var relocated = relocateJar(dependency, file);
                    if (!relocated) {
                        plugin.getPluginLogger().err("Failed to relocate " + dependency.filename() + "!");
                        continue;
                    } else {
                        file = new File(libDir, dependency.filename());
                    }
                }
                access.addURL(file.toURI().toURL());
            } catch (IOException e) {
                plugin.getPluginLogger().err("Failed to load " + dependency.filename() + "!");
            }
        }
    }

    private boolean relocateJar(Dependency dependency, File downloadedDep) {
        if (!relocatorAvailable) return false;
        plugin.getPluginLogger().debug("Relocating " + dependency.filename() + "...");
        try {
            var original = new File(libDir, dependency.filename().replace(".jar", "") + "-original.jar");
            if (original.exists()) original.delete();

            Files.copy(downloadedDep.toPath(), original.toPath());
            var relocated = new File(libDir, dependency.filename());

            var relocation = dependency.relocation();
            var rules = List.of(new Relocation(relocation[0], relocation[1]));
            var r = new JarRelocator(original, relocated, rules);
            r.run();
            original.delete();
            plugin.getPluginLogger().debug("Relocated " + dependency.filename() + "!");
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void downloadDependencies(ImmutableList<Dependency> dependencyList) {
        for (Dependency dependency : dependencyList) {
            var file = new File(libDir, dependency.filename());
            if (file.exists()) continue;
            try (var in = new URL(dependency.url()).openStream()) {
                if (downloadCheckedDep(dependency))
                    plugin.getPluginLogger().debug("Downloaded " + dependency.filename() + "!");
                else throw new IOException();
            } catch (IOException e) {
                plugin.getPluginLogger().err("Failed to download " + dependency.filename() + "!");
                plugin.getPluginLogger().err("Please download it manually from " + dependency.url() + " and put it in the lib folder.");
            }
        }
    }


    /**
     * Function to get all the dependencies in the runtime-deps.json file.
     */
    private ImmutableList<Dependency> readDependencies(String fileName) {
        var is = plugin.getResource(fileName);
        if (is == null) {
            plugin.getLogger().warning("Could not find " + fileName + " in the jar file.");
            return ImmutableList.of();
        }
        var builder = ImmutableList.<Dependency>builder();
        String jsonStr = null;
        try {
            jsonStr = CharStreams.toString(new InputStreamReader(is, Charsets.UTF_8));
        } catch (IOException e) {
            return ImmutableList.of();
        }
        var json = JsonParser.parseString(jsonStr).getAsJsonObject();
        for (var entry : json.entrySet()) {
            var obj = entry.getValue().getAsJsonObject();
            var dependency = new Gson().fromJson(obj, Dependency.class);
            plugin.getPluginLogger().debug("Dependency: " + dependency);
            builder.add(dependency);
        }
        return builder.build();
    }


}
