plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    alias(libs.plugins.testballoon) apply false
}

group = "com.yonatankarp"
// Releases pass -PreleaseVersion=<tag> (a leading "v" is stripped); otherwise a SNAPSHOT.
version = providers.gradleProperty("releaseVersion").map { it.removePrefix("v") }.getOrElse("0.1.0-SNAPSHOT")

subprojects {
    repositories {
        mavenCentral()
    }
}
