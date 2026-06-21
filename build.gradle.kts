plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    alias(libs.plugins.testballoon) apply false
}

group = "com.yonatankarp"
// The release workflow writes `version=<tag>` into gradle.properties (a leading "v" is
// stripped); other builds default to a SNAPSHOT.
version = providers.gradleProperty("version").map { it.removePrefix("v") }.getOrElse("0.1.0-SNAPSHOT")

subprojects {
    repositories {
        mavenCentral()
    }
}
