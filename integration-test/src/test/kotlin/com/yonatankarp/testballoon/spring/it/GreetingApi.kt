package com.yonatankarp.testballoon.spring.it

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@SpringBootApplication
class TestApplication

interface GreetingService {
    fun greet(name: String): String
}

@RestController
class GreetingController(private val service: GreetingService) {
    @GetMapping("/greet/{name}")
    fun greet(@PathVariable name: String): Map<String, String> =
        mapOf("message" to service.greet(name))
}
