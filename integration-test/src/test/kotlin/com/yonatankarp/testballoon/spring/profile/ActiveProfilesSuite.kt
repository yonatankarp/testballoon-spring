package com.yonatankarp.testballoon.spring.profile

import com.yonatankarp.testballoon.spring.SpringTestConfig
import com.yonatankarp.testballoon.spring.springTest
import de.infix.testBalloon.framework.core.testSuite
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile
import org.springframework.test.context.ActiveProfiles

fun interface Endpoint {
    fun url(): String
}

@SpringBootConfiguration
class EnvironmentConfig {
    @Bean
    @Profile("staging")
    fun stagingEndpoint(): Endpoint = Endpoint { "https://staging.example.com" }

    @Bean
    @Profile("prod")
    fun prodEndpoint(): Endpoint = Endpoint { "https://example.com" }
}

@SpringBootTest(classes = [EnvironmentConfig::class])
@ActiveProfiles("staging")
class StagingContext : SpringTestConfig()

// @ActiveProfiles on the carrier selects which profile-specific beans the context holds.
val ActiveProfilesSuite by testSuite {
    springTest<StagingContext> {
        test("only the bean for the active profile is present") {
            check(bean<Endpoint>().url() == "https://staging.example.com")
            check(applicationContext.environment.activeProfiles.contains("staging"))
        }
    }
}
