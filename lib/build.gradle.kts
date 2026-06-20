plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.spring.dependency.management)
    `java-library`
    `maven-publish`
}

group = rootProject.group
version = rootProject.version

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

dependencies {
    api(libs.testballoon.core)
    api(libs.spring.test)
    api(libs.spring.boot.test)
    api(libs.spring.boot.test.autoconfigure)
    api(libs.mockk)
    implementation(libs.kotlin.reflect)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "testballoon-spring"
            from(components["java"])
        }
    }
}
