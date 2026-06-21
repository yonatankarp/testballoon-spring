package com.yonatankarp.testballoon.spring.property

import com.yonatankarp.testballoon.spring.SpringTestConfig
import com.yonatankarp.testballoon.spring.springTest
import de.infix.testBalloon.framework.core.testSuite
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import org.springframework.test.context.TestPropertySource

@SpringBootConfiguration
class PropertyConfig

// An extra @Configuration pulled in via @Import on the carrier.
@Configuration
class ImportedConfig {
    @Bean
    fun importMarker(): String = "imported-bean-present"
}

// Reads an injected property the idiomatic way (constructor @Value on a real bean).
@Component
class GreetingProperties(@param:Value("\${app.greeting}") val greeting: String)

@SpringBootTest(
    classes = [PropertyConfig::class, GreetingProperties::class],
    properties = ["app.greeting=Hello from properties"],
)
@TestPropertySource(properties = ["app.region=eu-central-1"])
@Import(ImportedConfig::class)
class PropertyContext : SpringTestConfig()

val PropertySuite by testSuite {
    springTest<PropertyContext> {
        test("a @SpringBootTest property is injected into a bean via @Value") {
            check(bean<GreetingProperties>().greeting == "Hello from properties")
        }

        test("properties are readable from the Environment") {
            val environment = bean<Environment>()
            check(environment.getProperty("app.greeting") == "Hello from properties")
            // From @TestPropertySource on the carrier.
            check(environment.getProperty("app.region") == "eu-central-1")
        }

        test("@Import on the carrier contributes the extra configuration's beans") {
            check(bean<String>("importMarker") == "imported-bean-present")
        }
    }
}
