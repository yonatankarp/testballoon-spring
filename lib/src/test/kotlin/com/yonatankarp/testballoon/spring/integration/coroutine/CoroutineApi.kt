package com.yonatankarp.testballoon.spring.integration.coroutine

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

// Isolated in its own package + app so the greeting app's component scan does not pick it up.
@SpringBootApplication
class CoroutineApp

interface SuspendGreeter {
    suspend fun greet(name: String): String
}

@RestController
class SuspendGreetingController(private val greeter: SuspendGreeter) {
    @GetMapping("/suspend-greet/{name}")
    suspend fun greet(@PathVariable name: String): Map<String, String> = mapOf("message" to greeter.greet(name))
}
