plugins {
    java
    `java-library`
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
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("net.kyori:adventure-api:4.26.1")
    testImplementation("net.kyori:adventure-text-minimessage:4.26.1")
    testImplementation("net.kyori:adventure-text-serializer-legacy:4.26.1")
    testImplementation("net.md-5:bungeecord-chat:1.20-R0.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
