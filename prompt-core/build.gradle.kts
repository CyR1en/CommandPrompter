plugins {
    java
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.yaml:snakeyaml:2.0")
    testImplementation("org.yaml:snakeyaml:2.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
