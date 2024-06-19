package com.cyr1en.commandprompter.dependencies;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.PluginLogger;
import com.cyr1en.commandprompter.util.Util;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import me.lucko.jarrelocator.JarRelocator;
import me.lucko.jarrelocator.Relocation;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DependencyLoader {

    private final URLClassLoaderAccess access;
    private final CommandPrompter plugin;
    private final File libDir;
    private final PluginLogger logger;

    public DependencyLoader(CommandPrompter plugin) {
        this.plugin = plugin;
        this.logger = plugin.getPluginLogger();
        access = URLClassLoaderAccess.create((URLClassLoader) plugin.getClass().getClassLoader());
        libDir = initLibDirectory();
    }

    private boolean downloadCheckedDep(Dependency dependency) throws IOException {
        var downloaded = dependency.downloadChecked(libDir);
        if (!downloaded) {
            logger.err("Failed to download " + dependency.getFileName() + "!");
            logger.err("Please download it manually from " + dependency.getURL() + " and put it in the lib folder.");
            sendBundledMessage();
        }
        return downloaded;
    }

    public boolean isClassLoaderAccessSupported() {
        return !access.getClass().getSimpleName().equals("Noop");
    }

    public boolean relocatorAvailable() {
        return CoreDependency.RELOCATOR.inClassPath();
    }

    public void sendBundledMessage() {
        logger.err("Alternatively, you can download the latest bundled version of CommandPrompter here:");
        logger.err("https://github.com/CyR1en/CommandPrompter/releases/latest");
    }

    public File initLibDirectory() {
        var dataFolder = plugin.getDataFolder();
        var libDir = new File(dataFolder, "lib");
        if (!libDir.exists())
            if (!libDir.mkdir()) {
                logger.err("Failed to create lib directory!");
                return null;
            }
        return libDir;
    }

    public void loadDependency() {
        var dependencies = readDependencies();
        removeUnknownJars(dependencies);
        downloadDependencies(dependencies);
        loadAll(dependencies);
        logger.info("Finished loading dependencies");
    }

    public boolean loadCoreDeps() {
        CoreDependency.initLogger(logger);
        var result = CoreDependency.loadAll(access, libDir);
        if (!result) sendBundledMessage();
        return result;
    }

    private void loadAll(ImmutableList<Dependency> dependencies) {
        for (Dependency dependency : dependencies) {
            var file = new File(libDir, dependency.getFileName());
            if (!file.exists())
                continue;
            try {
                var relocation = dependency.getRelocation();
                logger.debug("Relocation: " + Arrays.toString(relocation));
                if (relocation.length == 2 && !relocation[0].isBlank() && !relocation[1].isBlank()) {
                    var relocated = relocateJar(dependency, file);
                    if (!relocated) {
                        logger.err("Failed to relocate " + dependency.getFileName() + "!");
                        continue;
                    } else {
                        file = new File(libDir, dependency.getFileName());
                    }
                }
                var url = file.toURI().toURL();
                access.addURL(url);
            } catch (IOException e) {
                logger.err("Failed to load " + dependency.getFileName() + "!");
            }
        }
    }

    private boolean relocateJar(Dependency dependency, File downloadedDep) {
        logger.debug("Relocating " + dependency.getFileName() + "...");
        try {
            var original = new File(libDir, dependency.getFileName().replace(".jar", "") + "-original.jar");
            if (original.exists())
                delete(original);

            Files.copy(downloadedDep.toPath(), original.toPath());
            var relocated = new File(libDir, dependency.getFileName());

            var relocation = dependency.getRelocation();
            var rules = List.of(new Relocation(relocation[0], relocation[1]));
            var r = new JarRelocator(original, relocated, rules);
            r.run();
            delete(original);
            logger.debug("Relocated " + dependency.getFileName() + "!");
            return true;
        } catch (IOException e) {
            logger.err("Failed to relocate " + dependency.getFileName() + "!");
            return false;
        }
    }

    private void delete(File file) {
        Util.deleteFile(file, f -> logger.err("Failed to delete " + f.getName() + "!"));
    }

    public void downloadDependencies(ImmutableList<Dependency> dependencyList) {
        for (Dependency dependency : dependencyList) {
            var file = new File(libDir, dependency.getFileName());
            if (file.exists())
                continue;
            try {
                if (downloadCheckedDep(dependency))
                    logger.debug("Downloaded " + dependency.getFileName() + "!");
                else
                    throw new IOException();
            } catch (IOException e) {
                logger.err("Failed to download " + dependency.getFileName() + "!");
                logger.err(
                        "Please download it manually from " + dependency.getURL() + " and put it in the lib folder.");
            }
        }
    }

    public void removeUnknownJars(List<Dependency> deps) {
        var unknowns = libDir.listFiles();
        if (unknowns == null)
            return;
        var depNames = new ArrayList<>(Arrays
                .stream(CoreDependency.values())
                .map(CoreDependency::getFileName)
                .toList());

        depNames.addAll(deps
                .stream()
                .map(Dependency::getFileName)
                .toList());

        Arrays.stream(unknowns).forEach(file -> {
            if (!depNames.contains(file.getName()))
                Util.deleteFile(file, f ->
                        logger.err("Failed to delete " + f.getName() + "!"));
        });
    }

    /**
     * Function to get all the dependencies in the runtime-deps.json file.
     */
    private ImmutableList<Dependency> readDependencies() {
        var is = plugin.getResource("runtime-deps.json");
        if (is == null) {
            logger.warn("Could not find " + "runtime-deps.json" + " in the jar file.");
            return ImmutableList.of();
        }
        var builder = ImmutableList.<Dependency>builder();
        String jsonStr;
        try {
            jsonStr = CharStreams.toString(new InputStreamReader(is, Charsets.UTF_8));
        } catch (IOException e) {
            return ImmutableList.of();
        }
        var json = new Gson().fromJson(jsonStr, JsonObject.class);
        for (var entry : json.entrySet()) {
            var obj = entry.getValue().getAsJsonObject();

            if (obj.has("default") && obj.has("mojang-mapped"))
                obj = Util.ServerType.isMojangMapped() ?
                        obj.getAsJsonObject("mojang-mapped") : obj.getAsJsonObject("default");

            logger.debug("Dependency JSON: " + obj.get("filename"));

            var dependency = new Gson().fromJson(obj, Dependency.class);
            logger.debug("Dependency: " + dependency);
            builder.add(dependency);
        }
        return builder.build();
    }

}
