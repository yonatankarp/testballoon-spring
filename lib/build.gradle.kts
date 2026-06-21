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
