package com.yonatankarp.testballoon.spring.integration.greeting

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@SpringBootApplication
class TestApplication

interface GreetingService {
    fun greet(name: String): String
}

/**
 * A real [GreetingService] shared by the suites that prove a mock or spy overrides an actual
 * bean. Plain class (no `@Component`) so it is instantiated only by the `@Bean` methods that
 * ask for it, never picked up by a component scan.
 */
class RealGreetingService : GreetingService {
    override fun greet(name: String): String = "REAL:$name"
}

@RestController
class GreetingController(private val service: GreetingService) {
    @GetMapping("/greet/{name}")
    fun greet(@PathVariable name: String): Map<String, String> = mapOf("message" to service.greet(name))
}
