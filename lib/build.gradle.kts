plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover)
    `java-library`
    `maven-publish`
}

group = rootProject.group
version = rootProject.version

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.jvm.get()))
    }
    explicitApi()
}

java {
    withSourcesJar()
}

ktlint {
    version.set(libs.versions.ktlint.cli.get())
}

extra["kotlin.version"] = libs.versions.kotlin.get()

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}")
    }
}

dependencies {
    api(libs.testballoon.core)
    api(libs.spring.test)
    api(libs.spring.boot.test)
    api(libs.spring.boot.test.autoconfigure)
    api(libs.mockk)

    // Test-scope only (never published): white-box unit tests for the internal error/guard
    // paths that the black-box testBalloon suites cannot reach. testBalloon's plugin can't be
    // applied to a module with main source, so the library's own unit tests use kotlin.test.
    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.junit.platform.launcher)
}

// The Spring BOM pins JUnit to 6.x; align the kotlin.test backend to a consistent
// JUnit Platform 1.13.4 / Jupiter 5.13.4 stack so the launcher can run the engine.
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.junit.platform") useVersion(libs.versions.junit.platform.get())
        if (requested.group == "org.junit.jupiter") useVersion(libs.versions.junit.jupiter.get())
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "testballoon-spring"
            from(components["java"])
            pom {
                name.set("testballoon-spring")
                description.set(
                    "Write Spring Boot tests as testBalloon suites with value-based mockBean<T>().",
                )
                url.set("https://github.com/yonatankarp/testballoon-spring")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("yonatankarp")
                        name.set("Yonatan Karp-Rudin")
                    }
                }
                scm {
                    url.set("https://github.com/yonatankarp/testballoon-spring")
                    connection.set("scm:git:https://github.com/yonatankarp/testballoon-spring.git")
                    developerConnection.set("scm:git:ssh://git@github.com/yonatankarp/testballoon-spring.git")
                }
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/yonatankarp/testballoon-spring")
            credentials {
                username = (findProperty("gpr.user") as String?) ?: System.getenv("GITHUB_ACTOR")
                password = (findProperty("gpr.key") as String?) ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
