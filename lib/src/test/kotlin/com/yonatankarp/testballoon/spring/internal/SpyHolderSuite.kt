package com.yonatankarp.testballoon.spring.internal

import de.infix.testBalloon.framework.core.testSuite

private open class Counter(var value: Int = 0) {
    open fun inc() {
        value++
    }
}

val SpyHolderSuite by testSuite {
    test("reading the spy before the context wraps it fails with an actionable message") {
        val holder = SpyHolder()

        val error = runCatching { holder.spy }.exceptionOrNull()

        check(error is IllegalStateException)
        check(error.message!!.contains("inside a test body"))
    }

    test("after wrap the spy is the wrapping instance") {
        val holder = SpyHolder()

        val spy = holder.wrap(Counter())

        check(holder.spy === spy)
    }
}
