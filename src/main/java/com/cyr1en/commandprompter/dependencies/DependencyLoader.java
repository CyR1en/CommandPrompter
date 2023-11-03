package com.cyr1en.commandprompter.dependencies;

import com.cyr1en.commandprompter.CommandPrompter;
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
import java.util.Arrays;
import java.util.List;

public class DependencyLoader {

    private final URLClassLoaderAccess access;
    private final CommandPrompter plugin;
    private final File libDir;

    public DependencyLoader(CommandPrompter plugin) {
        this.plugin = plugin;
        access = URLClassLoaderAccess.create((URLClassLoader) plugin.getClass().getClassLoader());
        libDir = initLibDirectory();
    }

    private boolean downloadCheckedDep(Dependency dependency) throws IOException {
        var downloaded = dependency.downloadChecked(libDir);
        if (!downloaded) {
            plugin.getPluginLogger().err("Failed to download " + dependency.getFileName() + "!");
            plugin.getPluginLogger()
                    .err("Please download it manually from " + dependency.getURL() + " and put it in the lib folder.");
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
        plugin.getPluginLogger()
                .err("Alternatively, you can download the latest bundled version of CommandPrompter here:");
        plugin.getPluginLogger().err("https://github.com/CyR1en/CommandPrompter/releases/latest");
    }

    public File initLibDirectory() {
        var dataFolder = plugin.getDataFolder();
        var libDir = new File(dataFolder, "lib");
        if (!libDir.exists())
            libDir.mkdir();
        return libDir;
    }

    public void loadDependency() {
        var dependencies = readDependencies("runtime-deps.json");
        downloadDependencies(dependencies);
        loadAll(dependencies);
        plugin.getPluginLogger().info("Finished loading dependencies");
    }

    public boolean loadCoreDeps() {
        CoreDependency.initLogger(plugin.getPluginLogger());
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
                plugin.getPluginLogger().debug("Relocation: " + Arrays.toString(relocation));
                if (relocation.length == 2 && !relocation[0].isBlank() && !relocation[1].isBlank()) {
                    var relocated = relocateJar(dependency, file);
                    if (!relocated) {
                        plugin.getPluginLogger().err("Failed to relocate " + dependency.getFileName() + "!");
                        continue;
                    } else {
                        file = new File(libDir, dependency.getFileName());
                    }
                }
                var url = file.toURI().toURL();
                if (url == null)
                    continue;
                access.addURL(url);
            } catch (IOException e) {
                plugin.getPluginLogger().err("Failed to load " + dependency.getFileName() + "!");
            }
        }
    }

    private boolean relocateJar(Dependency dependency, File downloadedDep) {
        plugin.getPluginLogger().debug("Relocating " + dependency.getFileName() + "...");
        try {
            var original = new File(libDir, dependency.getFileName().replace(".jar", "") + "-original.jar");
            if (original.exists())
                original.delete();

            Files.copy(downloadedDep.toPath(), original.toPath());
            var relocated = new File(libDir, dependency.getFileName());

            var relocation = dependency.getRelocation();
            var rules = List.of(new Relocation(relocation[0], relocation[1]));
            var r = new JarRelocator(original, relocated, rules);
            r.run();
            original.delete();
            plugin.getPluginLogger().debug("Relocated " + dependency.getFileName() + "!");
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void downloadDependencies(ImmutableList<Dependency> dependencyList) {
        for (Dependency dependency : dependencyList) {
            var file = new File(libDir, dependency.getFileName());
            if (file.exists())
                continue;
            try {
                if (downloadCheckedDep(dependency))
                    plugin.getPluginLogger().debug("Downloaded " + dependency.getFileName() + "!");
                else
                    throw new IOException();
            } catch (IOException e) {
                plugin.getPluginLogger().err("Failed to download " + dependency.getFileName() + "!");
                plugin.getPluginLogger().err(
                        "Please download it manually from " + dependency.getURL() + " and put it in the lib folder.");
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
        var json = new Gson().fromJson(jsonStr, JsonObject.class);
        for (var entry : json.entrySet()) {
            var obj = entry.getValue().getAsJsonObject();
            var dependency = new Gson().fromJson(obj, Dependency.class);
            plugin.getPluginLogger().debug("Dependency: " + dependency);
            builder.add(dependency);
        }
        return builder.build();
    }

}
