package com.cyr1en.commandprompter;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

@SuppressWarnings("UnstableApiUsage")
public class CommandPrompterLoader implements PluginLoader {

    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();
        resolver.addRepository(new RemoteRepository.Builder("central", "default", "https://maven-central.storage-download.googleapis.com/maven2").build());
        resolver.addDependency(new Dependency(new DefaultArtifact("org.openjdk.nashorn:nashorn-core:15.4"), null));
        classpathBuilder.addLibrary(resolver);
    }
}
