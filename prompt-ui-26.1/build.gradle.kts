plugins {
    java
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    paperweight.paperDevBundle("26.1.2.build.+")
    compileOnly(project(":prompt-ui-api"))
    implementation(project(":prompt-core"))
}

paperweight {
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.reobfJar.configure {
    enabled = false
}

tasks.withType<PublishToMavenRepository>().configureEach {
    enabled = false
}
