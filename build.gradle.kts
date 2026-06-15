allprojects {
    group = "dev.cyr1en"
    version = "3.0.0"
}

subprojects {
    apply(plugin = "maven-publish")

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
