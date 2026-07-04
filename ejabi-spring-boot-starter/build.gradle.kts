plugins {
    alias(libs.plugins.kotlin.jvm)
    id("org.jetbrains.kotlin.kapt")
    alias(libs.plugins.kotlin.plugin.spring)
    alias(libs.plugins.dokka)
    alias(libs.plugins.dokka.javadoc)
    `maven-publish`
    signing
}

dependencies {
    api(project(":ejabi-core"))
    api(platform(libs.spring.boot.dependencies))
    api("org.springframework.boot:spring-boot-starter-web")

    kapt(libs.spring.boot.configuration.processor)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
}

java {
    withSourcesJar()
}

val dokkaJavadocJar by tasks.registering(Jar::class) {
    description = "A Javadoc JAR containing Dokka Javadoc output."
    archiveClassifier = "javadoc"
    from(tasks.named("dokkaGeneratePublicationJavadoc"))
}

dokka {
    moduleName = "Ejabi Spring Boot Starter"
    moduleVersion = project.version.toString()
    dokkaSourceSets.configureEach {
        documentedVisibilities = setOf(org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier.Public)
        skipEmptyPackages = true
    }
}

tasks.withType<org.gradle.api.publish.tasks.GenerateModuleMetadata>().configureEach {
    enabled = false
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "ejabi-spring-boot-starter"
            from(components["java"])
            artifact(dokkaJavadocJar)

            pom {
                name.set("Ejabi Spring Boot Starter")
                description.set("Spring Boot starter for the Ejabi template engine")
                url.set("https://github.com/yoshisuproject/ejabi")
                inceptionYear.set("2026")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("yoshisuproject")
                        name.set("YoshisuProject")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/yoshisuproject/ejabi")
                    developerConnection.set("scm:git:ssh://git@github.com/yoshisuproject/ejabi.git")
                    url.set("https://github.com/yoshisuproject/ejabi")
                }
                issueManagement {
                    system.set("GitHub Issues")
                    url.set("https://github.com/yoshisuproject/ejabi/issues")
                }
            }
        }
    }
    repositories {
        val releaseRepositoryUrl = providers.gradleProperty("releaseRepositoryUrl").orNull
        if (!releaseRepositoryUrl.isNullOrBlank()) {
            maven {
                name = "release"
                url = uri(releaseRepositoryUrl)
                credentials {
                    username = providers.gradleProperty("releaseRepositoryUsername").orNull
                    password = providers.gradleProperty("releaseRepositoryPassword").orNull
                }
            }
        }
    }
}

signing {
    val signingKeyId = providers.gradleProperty("signingKeyId").orNull
    val signingKey = providers.gradleProperty("signingKey").orNull
    val signingPassword = providers.gradleProperty("signingPassword").orNull

    if (!signingKey.isNullOrBlank()) {
        useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
        sign(publishing.publications["maven"])
    }
}
