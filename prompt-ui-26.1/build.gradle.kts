plugins {
    java
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    paperweight.paperDevBundle("26.1.2.build.74-stable")
    compileOnly(project(":prompt-ui-api"))
    implementation(project(":prompt-core"))

    testImplementation(project(":prompt-ui-api"))
    testImplementation("org.mockito:mockito-core:5.14.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    jvmArgs("-Dnet.bytebuddy.experimental=true")
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
