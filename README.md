# testballoon-spring

Write Spring Boot tests as [testBalloon](https://github.com/infix-de/testBalloon)
suites, with idiomatic value-based mocks (`val x = mockBean<T>()`) instead of
`@MockkBean lateinit var` field injection.

It drives Spring's own `TestContextManager` from testBalloon fixtures, so
`@WebFluxTest`, `@SpringBootTest`, `@DataJdbcTest` and the rest work as usual.

## Usage

```kotlin
@WebFluxTest(GreetingController::class)
class GreetingWebContext : SpringTestConfig()   // bodyless carrier for the slice annotations

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

- `springTest<C> { }` — inside a `testSuite { }`, boots the Spring context
  described by the carrier class `C` and scopes the block to it.
- `mockBean<T>()` — a mockk mock registered as a primary bean in the context and
  reset before each test.
- `bean<T>()` — any bean from the live `ApplicationContext` (e.g. `WebTestClient`).

The suite **must** be declared `val X by testSuite { … }` directly — the
testBalloon compiler plugin only registers suites written at that call site, so a
wrapper around `testSuite` would never be discovered. The Spring setup therefore
nests inside via `springTest`.

## Important: suites live in test-only modules

The testBalloon Gradle plugin cannot be applied to a module that has `main`
Kotlin source — Kotlin 2.4.0's Build Tools API throws
`getPluginClasspaths() is null` on the main compilation, where the plugin is not
applicable. So:

- This library (`lib`) is a plain `kotlin.jvm` module — **no** testBalloon plugin.
- Test suites go in a **test-only** module (no `src/main`) that applies the
  testBalloon plugin and depends on this library. See `integration-test/`.

## Build gotchas (already handled in the sample modules)

- The Spring Boot BOM pins `kotlin-build-tools-impl` below your Kotlin version,
  triggering a `KotlinWrapperPre2_4_0` NPE. Set
  `extra["kotlin.version"] = "<your kotlin>"` before importing the BOM.
- testBalloon's engine needs `junit-platform:1.13.4`; the Spring BOM pins it
  lower, breaking discovery. Force `org.junit.platform`/`org.junit.jupiter` to
  aligned versions and add `testRuntimeOnly("org.junit.platform:junit-platform-launcher")`.

## Status

Pre-release (`0.1.0-SNAPSHOT`). Validated against Spring Boot 4.1.0, Kotlin 2.4.0,
testBalloon 1.0.1, JVM 25.

Not yet supported: method-level test annotations (`@Sql` etc.), cross-suite
context caching (each suite builds its own context because it owns its mock
instances).
