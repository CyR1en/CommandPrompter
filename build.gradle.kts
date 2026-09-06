import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
    id("com.diffplug.spotless") version "7.0.2" apply false
}

allprojects {
    group = "dev.cyr1en"
    version = "3.3.0"
}

tasks.register("printVersion") {
    description = "Print the Gradle project version for release checks."
    doLast { println(project.version) }
}

subprojects {
    apply(plugin = "maven-publish")

    plugins.withId("java") {
        configure<JavaPluginExtension> {
            toolchain.languageVersion.set(JavaLanguageVersion.of(25))
        }
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }

        apply(plugin = "com.diffplug.spotless")
        configure<SpotlessExtension> {
            java {
                googleJavaFormat("1.27.0")
                removeUnusedImports()
                target("src/**/*.java")
            }
        }
    }

    configure<PublishingExtension> {
        repositories {
            maven {
                name = "Kakuno"
                url = uri("https://repo.cyr1en.dev/snapshots")
                credentials {
                    username = System.getenv("KAKUNO_USER")
                    password = System.getenv("KAKUNO_TOKEN")
                }
            }
        }

        publications {
            create<MavenPublication>("mavenJava") {
                afterEvaluate {
                    if (project.name != "prompt-paper") {
                        if (components.findByName("java") != null) {
                            from(components["java"])
                        }
                    }
                }
            }
        }
    }
}
