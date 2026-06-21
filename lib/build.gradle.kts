plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.testballoon)
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

    // Test-scope only (never published): the testBalloon suites that exercise this library,
    // both the white-box unit suites for internal error/guard paths and the black-box
    // integration suites under the `integration` package that drive real Spring contexts.
    testImplementation(libs.spring.boot.webflux.test)
    testImplementation(libs.spring.boot.starter.webflux)
    testImplementation(libs.json.path)
    testImplementation(libs.kotlinx.coroutines.reactor)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// testBalloon's engine needs JUnit Platform 1.13.4; the Spring Boot BOM pins it lower.
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.junit.platform") useVersion(libs.versions.junit.platform.get())
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

kover {
    reports {
        // Generated/structural members the suites cannot meaningfully reach; excluded so the
        // gate reflects real logic coverage rather than boilerplate.
        filters {
            excludes {
                // Plugin entry point: instantiated reflectively by Spring's
                // ContextCustomizerFactory SPI, never by the suites themselves.
                classes("com.yonatankarp.testballoon.spring.internal.MockBeanContextCustomizerFactory")
            }
        }

        verify {
            // Combined coverage of the black-box integration suites and the white-box unit
            // suites (achieved: line ~97%, branch ~96%). Gate set just below, with a little
            // headroom. The lone uncovered branch is an obscure naming edge (several named mocks
            // of a type that also has a real bean).
            rule("lib line coverage") {
                minBound(95)
            }
            rule("lib branch coverage") {
                bound {
                    coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.BRANCH
                    minValue = 90
                }
            }
        }
    }
}

// Run the coverage gate and emit the JaCoCo-format XML (consumed by the CI coverage comment)
// as part of `check`/`build`.
tasks.named("check") {
    dependsOn("koverVerify", "koverXmlReport")
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
