package com.yonatankarp.testballoon.spring.it

import com.yonatankarp.testballoon.spring.SpringTestConfig
import com.yonatankarp.testballoon.spring.springTest
import de.infix.testBalloon.framework.core.testSuite
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.ApplicationContext

@SpringBootTest
@AutoConfigureWebTestClient
class SharedContext : SpringTestConfig()

// One context is built per springTest block and reused across its tests.
val SharedContextSuite by testSuite {
    springTest<SharedContext> {
        mockBean<GreetingService>()
        var firstContext: ApplicationContext? = null

        test("the context is captured") {
            firstContext = applicationContext
        }

        test("the same context instance is reused for the next test") {
            check(applicationContext === firstContext)
        }
    }
}
