# testballoon-spring

Write Spring Boot tests as [testBalloon](https://github.com/infix-de/testBalloon)
suites, with idiomatic value-based mocks (`val x = mockBean<T>()`) instead of
`@MockkBean lateinit var` field injection.

It drives Spring's own `TestContextManager` from testBalloon fixtures, so
`@WebFluxTest`, `@SpringBootTest`, `@DataJdbcTest` and the rest work as usual.
The published artifact pulls only Spring's test infrastructure plus testBalloon
and mockk — no JUnit Jupiter, Mockito or AssertJ.

## Usage

```kotlin
@WebFluxTest(GreetingController::class)
class GreetingWebContext : SpringTestConfig()   // carrier for the slice annotations

val GreetingControllerSuite by testSuite {
    springTest<GreetingWebContext> {
        val greetingService = mockBean<GreetingService>()

        test("GET /greet/{name} returns the greeting") {
            every { greetingService.greet("Ada") } returns "Hello, Ada!"

            bean<WebTestClient>()
                .get().uri("/greet/Ada")
                .exchange()
                .expectStatus().isOk
                .expectBody().jsonPath("$.message").isEqualTo("Hello, Ada!")
        }
    }
}
```

The suite **must** be declared `val X by testSuite { … }` directly — testBalloon's
compiler plugin only registers suites written at that call site, so the Spring
setup nests inside via `springTest`.

### API

- `springTest<C> { }` — inside a `testSuite { }`, boots the Spring context described
  by carrier class `C` and scopes the block to it. The context is built once per
  block and shared by its tests.
- `mockBean<T>(name = null, relaxed = false)` — a mockk mock registered as a primary
  bean (or under `name`, for `@Qualifier` injection points), reset before each test.
- `bean<T>()` / `bean<T>(name)` — look up a bean from the live `ApplicationContext`.
- `applicationContext` — the context itself, as an escape hatch.

### Works with any slice or the full context

```kotlin
@SpringBootTest
@AutoConfigureWebTestClient
class GreetingAppContext : SpringTestConfig()

val GreetingAppSuite by testSuite {
    springTest<GreetingAppContext> {
        val greetingService = mockBean<GreetingService>()
        test("full context") { /* … */ }
    }
}
```

See [`integration-test/`](integration-test/src/test/kotlin) for runnable `@WebFluxTest`,
`@SpringBootTest`, and named-bean examples.

## Installation

Published to GitHub Packages. Add the repository (GitHub Packages requires
authentication even for reads) and the dependency to a **test-only** module:

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/yonatankarp/testballoon-spring")
        credentials {
            username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
            password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    testImplementation("com.yonatankarp:testballoon-spring:0.1.0-SNAPSHOT")
}
```

## Module layout (important)

The testBalloon Gradle plugin cannot be applied to a module that has `main` Kotlin
source — Kotlin 2.4.0's Build Tools API throws `getPluginClasspaths() is null` on
the main compilation, where the plugin is not applicable. So testBalloon suites
live in a **test-only module** (no `src/main`) that applies the plugin and depends
on this library. The library itself is a plain `kotlin.jvm` module.

Two build alignments are needed in the test module (see
[`integration-test/build.gradle.kts`](integration-test/build.gradle.kts)):

- The Spring Boot BOM pins `kotlin-build-tools-impl` below your Kotlin version,
  triggering a `KotlinWrapperPre2_4_0` NPE. Set
  `extra["kotlin.version"] = "<your kotlin>"` before importing the BOM.
- testBalloon's engine needs JUnit Platform 1.13.4; the Spring BOM pins it lower.
  Force `org.junit.platform` to 1.13.4 and add
  `testRuntimeOnly("org.junit.platform:junit-platform-launcher")`.

## Lifecycle

One context per `springTest` block, shared across its tests. Mocks are reset
(`clearMocks`) before each test, so every test re-stubs from scratch. Each suite
owns its mock instances, so two suites do not share a cached context.

Not yet supported: method-level test annotations (`@Sql` etc.) and cross-suite
context caching.

## Compatibility

Spring Boot 4.1.0 · Kotlin 2.4.0 · testBalloon 1.0.1 · JVM 25. Pre-release
(`0.1.0-SNAPSHOT`).

## License

[Apache-2.0](LICENSE).
