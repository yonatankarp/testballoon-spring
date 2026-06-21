plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.testballoon)
    alias(libs.plugins.ktlint)
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
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
