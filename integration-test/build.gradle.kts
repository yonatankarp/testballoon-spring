plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.testballoon)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover)
}

ktlint {
    version.set(libs.versions.ktlint.cli.get())
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.jvm.get()))
    }
}

extra["kotlin.version"] = libs.versions.kotlin.get()

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}")
    }
}

configurations.all {
    // testBalloon's engine needs JUnit Platform 1.13.4; the Spring BOM pins it lower.
    resolutionStrategy.eachDependency {
        if (requested.group == "org.junit.platform") useVersion(libs.versions.junit.platform.get())
    }
}

dependencies {
    testImplementation(project(":lib"))
    testImplementation(libs.spring.boot.webflux.test)
    testImplementation(libs.spring.boot.starter.webflux)
    testImplementation(libs.json.path)
    testImplementation(libs.kotlinx.coroutines.reactor)
    testRuntimeOnly(libs.junit.platform.launcher)

    // The lib has no tests of its own; its classes are exercised black-box by the suites
    // here. Aggregating lib into this module's Kover report attributes that execution to
    // the lib's bytecode, producing a single cross-module coverage report and gate.
    kover(project(":lib"))
}

tasks.withType<Test> {
    useJUnitPlatform()
}

kover {
    reports {
        // Generated/structural members the black-box suites cannot meaningfully reach;
        // excluded so the gate reflects real logic coverage rather than boilerplate.
        filters {
            excludes {
                // Plugin entry point: instantiated reflectively by Spring's
                // ContextCustomizerFactory SPI, never by the suites themselves.
                classes("com.yonatankarp.testballoon.spring.MockBeanContextCustomizerFactory")
            }
        }

        verify {
            // Thresholds sit at (line) / just below (branch) the coverage actually achieved
            // by the black-box suites. The uncovered remainder is exclusively defensive guards
            // and error paths that only surface as failed context loads (carrier without a
            // no-arg ctor; spyBean no-bean / ambiguous-bean errors; spying a @Primary bean) and
            // cannot be asserted inside a passing suite.
            rule("lib line coverage") {
                minBound(90)
            }
            rule("lib branch coverage") {
                bound {
                    coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.BRANCH
                    minValue = 65
                }
            }
        }
    }
}

// Run the coverage gate as part of `check`/`build`.
tasks.named("check") {
    dependsOn("koverVerify")
}
