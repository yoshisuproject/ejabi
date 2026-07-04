plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.dokka.javadoc) apply false
    alias(libs.plugins.ktlint.gradle) apply false
}

group = "com.yoshisuproject"
version = "0.1.0"

val ktlintVersion: String = libs.versions.ktlint.get()

allprojects {
    group = "com.yoshisuproject"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}

tasks.register("releaseCheck") {
    description = "Builds all modules, publishes them to the local Maven cache, and verifies the published POMs exist."
    group = "verification"
    dependsOn(":ejabi-core:build", ":ejabi-spring-boot-starter:build")
    dependsOn(":ejabi-core:publishToMavenLocal", ":ejabi-spring-boot-starter:publishToMavenLocal")
    val expectedPoms =
        listOf("ejabi", "ejabi-spring-boot-starter").map { artifactId ->
            java.io.File(
                System.getProperty("user.home"),
                ".m2/repository/com/yoshisuproject/$artifactId/$version/$artifactId-$version.pom"
            )
        }
    doLast {
        expectedPoms.forEach { pom ->
            check(pom.isFile) { "Missing published POM: ${pom.absolutePath}" }
        }
    }
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            jvmToolchain(17)
            compilerOptions {
                freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
            }
        }
        tasks.withType<Test> {
            useJUnitPlatform()
        }

        apply(plugin = "org.jlleitschuh.gradle.ktlint")
        extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
            version.set(ktlintVersion)
        }
    }
}
