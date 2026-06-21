package com.yonatankarp.testballoon.spring

/**
 * Base class for the small "carrier" class that holds the Spring test annotations
 * (`@WebFluxTest`, `@SpringBootTest`, `@DataJdbcTest`, …) for a [springTest] block.
 *
 * Spring discovers what context to load by reading those annotations off a [Class],
 * so a class is required even though it carries no state:
 *
 * ```
 * @WebFluxTest(GreetingController::class)
 * class GreetingWebContext : SpringTestConfig()
 * ```
 *
 * Subclasses must have a no-arg constructor (the default for a class with no
 * declared constructors).
 */
public abstract class SpringTestConfig {
    // Public (not internal) on purpose: it is looked up reflectively by name, and
    // Kotlin mangles internal members' JVM names. TestContextManager.beforeTestMethod
    // needs a Method belonging to the carrier; this stand-in avoids synthesizing one.
    public fun springTestBalloonCarrier(): Unit = Unit
}
