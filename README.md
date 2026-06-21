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
- `spyBean<T>(name = null)` — a mockk spy wrapping the **real** bean of type `T`
  (Spring `@SpyBean` parity): unstubbed calls run the real implementation and are
  recorded; individual calls can be stubbed. Reset before each test. See below.
- `bean<T>()` / `bean<T>(name)` — look up a bean from the live `ApplicationContext`.
- `applicationContext` — the context itself, as an escape hatch.

### Spy beans

`spyBean<T>()` wraps the real bean rather than replacing it. The real bean only exists
once the context is built, so it is returned through a property delegate (read it inside
a test body, not at declaration time):

```kotlin
val SpyBeanSuite by testSuite {
    springTest<SpyContext> {
        val calculator by spyBean<Calculator>()

        test("real by default, stubbed selectively") {
            check(calculator.square(3) == 9)            // runs the real implementation
            every { calculator.square(3) } returns 1000
            check(calculator.square(3) == 1000)         // stub wins
            check(calculator.square(4) == 16)           // other args still real
            verify { calculator.square(4) }             // calls are recorded
        }
    }
}
```

### Profiles, properties and extra configuration

These all live as annotations on the carrier (Spring reads them off the `Class`):

```kotlin
@SpringBootTest(classes = [AppConfig::class], properties = ["app.greeting=Hello"])
@ActiveProfiles("staging")          // selects profile-specific beans
@TestPropertySource(properties = ["app.region=eu-central-1"])
@Import(ExtraConfig::class)         // contributes the extra config's beans
class AppContext : SpringTestConfig()
```

Read properties the Kotlin-first way through the `Environment` bean (no field injection):

```kotlin
test("properties resolve") {
    val env = bean<Environment>()
    check(env.getProperty("app.region") == "eu-central-1")
}
```

`@DynamicPropertySource` works too — declare it as a `@JvmStatic` method on the carrier's
companion object; the driven `TestContextManager` picks it up exactly as it does off a
JUnit test class.

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

The bridge is slice-agnostic: it drives Spring's own `TestContextManager`, so any slice
that works off a `Class` works here. The [`integration` suites](lib/src/test/kotlin/com/yonatankarp/testballoon/spring/integration)
have runnable examples for `@WebFluxTest`, `@SpringBootTest`, `@JsonTest` (a non-WebFlux
slice), `@ActiveProfiles`, `@TestPropertySource`/`properties`, `@Import`,
`@DynamicPropertySource`, named beans, and spy beans.

> Note: under Spring Boot 4.1 the `@JsonTest` `ObjectMapper` is Jackson 3
> (`tools.jackson.databind.ObjectMapper`), not the Jackson 2 `com.fasterxml.jackson.*`.

## Installation

Published to GitHub Packages. Add the repository (GitHub Packages requires
authentication even for reads) and the dependency to the test configuration of the
module where you write suites:

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

## Build setup (important)

Apply the testBalloon Gradle plugin to the module whose tests you write, and add this
library to its test configuration. The plugin applies cleanly to a module that also has
`main` Kotlin source, so no separate test-only module is needed — this repository keeps the
library and its suites in one module (see [`lib/build.gradle.kts`](lib/build.gradle.kts)).

Two build alignments are needed alongside the Spring Boot BOM:

- The Spring Boot BOM pins `kotlin-build-tools-impl` below your Kotlin version. Without the
  alignment the testBalloon plugin fails with a `KotlinWrapperPre2_4_0` NPE
  (`getPluginClasspaths() is null`). Set `extra["kotlin.version"] = "<your kotlin>"` before
  importing the BOM.
- testBalloon's engine needs JUnit Platform 1.13.4; the Spring BOM pins it lower. Force
  `org.junit.platform` to 1.13.4 and add
  `testRuntimeOnly("org.junit.platform:junit-platform-launcher")`.

## Lifecycle

One context per `springTest` block, shared across its tests. Mocks are reset
(`clearMocks`) before each test, so every test re-stubs from scratch. Each suite
owns its mock instances, so two suites do not share a cached context.

### Not feasible (by design)

- **Method-level test annotations** (`@Sql`, `@Transactional`, `@DirtiesContext` on a
  method, etc.) — Spring reads these off a per-test JVM `Method`, but testBalloon tests
  are lambdas with no backing method to annotate, and we deliberately don't synthesize
  one. Use a testBalloon fixture for per-test setup/teardown instead, or put a
  class-level equivalent on the carrier where one exists.
- **Cross-suite context caching** — each suite owns its own mock/spy instances, so two
  suites never share a cached context (Spring keys caching on customizer equality).

## Compatibility

Spring Boot 4.1.0 · Kotlin 2.4.0 · testBalloon 1.0.1 · JVM 25. Pre-release
(`0.1.0-SNAPSHOT`).

## License

[Apache-2.0](LICENSE).
