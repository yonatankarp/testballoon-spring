package com.yonatankarp.testballoon.spring.integration.mockprimary

import com.yonatankarp.testballoon.spring.SpringTestConfig
import com.yonatankarp.testballoon.spring.integration.greeting.GreetingService
import com.yonatankarp.testballoon.spring.integration.greeting.RealGreetingService
import com.yonatankarp.testballoon.spring.springTest
import de.infix.testBalloon.framework.core.testSuite
import io.mockk.every
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
class PrimaryRealConfig {
    @Bean
    @Primary
    fun realGreeter(): GreetingService = RealGreetingService()
}

@SpringBootTest(classes = [PrimaryRealConfig::class])
class MockPrimaryContext : SpringTestConfig()

val MockPrimaryBeanSuite by testSuite {
    springTest<MockPrimaryContext> {
        val svc = mockBean<GreetingService>()
        test("a @Primary real bean is replaced by the mock and resolves uniquely by type") {
            every { svc.greet("x") } returns "MOCK"
            check(bean<GreetingService>().greet("x") == "MOCK")
        }
    }
}
