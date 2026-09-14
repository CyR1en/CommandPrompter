plugins {
    java
    `java-library`
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.opencollab.dev/main/")
}

dependencies {
    api(project(":prompt-core"))
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.74-stable")
    compileOnly("org.geysermc.geyser:api:2.8.3-SNAPSHOT")

    testImplementation("io.papermc.paper:paper-api:26.1.2.build.74-stable")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("net.kyori:adventure-api:4.26.1")
    testImplementation("net.kyori:adventure-text-minimessage:4.26.1")
    testImplementation("net.kyori:adventure-text-serializer-legacy:4.26.1")
    testImplementation("net.md-5:bungeecord-chat:1.20-R0.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
