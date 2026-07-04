plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.dokka)
    alias(libs.plugins.dokka.javadoc)
    antlr
    `maven-publish`
    signing
}

dependencies {
    antlr(libs.antlr.antlr4)
    implementation(libs.antlr.runtime)
    implementation(libs.kotlin.reflect)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
}

configurations {
    compileClasspath {
        exclude(group = "org.antlr", module = "antlr4")
    }
    runtimeClasspath {
        exclude(group = "org.antlr", module = "antlr4")
    }
}

sourceSets {
    main {
        kotlin.srcDir(layout.buildDirectory.dir("generated-src/antlr/main"))
    }
}

tasks.generateGrammarSource {
    arguments.addAll(
        listOf(
            "-visitor",
            "-long-messages",
        ),
    )
    outputDirectory =
        layout.buildDirectory
            .dir("generated-src/antlr/main")
            .get()
            .asFile
    packageName = "com.yoshisuproject.ejabi.internal.parser"
}

java {
    withSourcesJar()
}

val dokkaJavadocJar by tasks.registering(Jar::class) {
    description = "A Javadoc JAR containing Dokka Javadoc output."
    archiveClassifier = "javadoc"
    from(tasks.named("dokkaGeneratePublicationJavadoc"))
}

tasks.compileKotlin {
    dependsOn(tasks.generateGrammarSource)
}

dokka {
    moduleName = "Ejabi"
    moduleVersion = project.version.toString()
    dokkaSourceSets.configureEach {
        documentedVisibilities = setOf(org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier.Public)
        skipEmptyPackages = true
        suppressGeneratedFiles = true
        perPackageOption {
            matchingRegex.set("""com\.yoshisuproject\.ejabi\.internal(\..*)?""")
            suppress = true
        }
    }
}

tasks.named<Javadoc>("javadoc") {
    exclude(
        "**/EjabiLexer.java",
        "**/EjabiParser*.java",
        "**/EjabiExpr*.java",
    )
}

tasks.named<Jar>("sourcesJar") {
    includeEmptyDirs = false
    exclude(
        "**/EjabiLexer.java",
        "**/EjabiParser*.java",
        "**/EjabiLexer.interp",
        "**/EjabiParser.interp",
        "**/EjabiLexer.tokens",
        "**/EjabiParser.tokens",
        "**/EjabiExpr*.java",
        "**/EjabiExpr*.interp",
        "**/EjabiExpr*.tokens",
    )
}

tasks.withType<org.gradle.api.publish.tasks.GenerateModuleMetadata>().configureEach {
    enabled = false
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "ejabi"
            from(components["java"])
            artifact(dokkaJavadocJar)

            pom {
                name.set("Ejabi")
                description.set("A Kotlin template engine inspired by Erubi")
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
                withXml {
                    val dependencies = asNode().get("dependencies") as groovy.util.NodeList
                    dependencies.filterIsInstance<groovy.util.Node>().forEach { dependencyContainer ->
                        val toolDependencies =
                            dependencyContainer.children().filterIsInstance<groovy.util.Node>().filter { dependency ->
                                val groupId = (dependency.get("groupId") as groovy.util.NodeList).text()
                                val artifactId = (dependency.get("artifactId") as groovy.util.NodeList).text()
                                groupId == "org.antlr" && artifactId == "antlr4"
                            }
                        toolDependencies.forEach { dependencyContainer.remove(it) }
                    }
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
