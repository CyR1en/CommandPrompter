plugins {
    java
    `java-library`
    id("com.diffplug.spotless") version "7.0.2"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

spotless {
    java {
        googleJavaFormat("1.27.0")
        target("src/**/*.java")
    }
}

dependencies {
    compileOnly("org.yaml:snakeyaml:2.0")
    testImplementation("org.yaml:snakeyaml:2.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

tasks.named("check") {
    dependsOn(tasks.named("spotlessCheck"))
}
