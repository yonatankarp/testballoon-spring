package com.yonatankarp.testballoon.spring.integration.named

import com.yonatankarp.testballoon.spring.SpringTestConfig
import com.yonatankarp.testballoon.spring.springTest
import de.infix.testBalloon.framework.core.testSuite
import io.mockk.every
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.test.context.SpringBootTest

interface Translator {
    fun translate(text: String): String
}

@SpringBootConfiguration
class NamedConfig

@SpringBootTest(classes = [NamedConfig::class])
class NamedBeanContext : SpringTestConfig()

val NamedBeanSuite by testSuite {
    springTest<NamedBeanContext> {
        val english = mockBean<Translator>(name = "english")
        val german = mockBean<Translator>(name = "german")

        test("named mocks are registered and resolved by bean name") {
            every { english.translate("hi") } returns "hi"
            every { german.translate("hi") } returns "hallo"

            check(bean<Translator>("english").translate("hi") == "hi")
            check(bean<Translator>("german").translate("hi") == "hallo")
        }
    }
}
