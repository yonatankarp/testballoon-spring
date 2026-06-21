package com.yonatankarp.testballoon.spring.json

import com.yonatankarp.testballoon.spring.SpringTestConfig
import com.yonatankarp.testballoon.spring.springTest
import de.infix.testBalloon.framework.core.testSuite
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.test.autoconfigure.json.JsonTest
import tools.jackson.databind.ObjectMapper

data class Coordinate(val lat: Double, val lon: Double)

// A slice still needs a @SpringBootConfiguration to anchor the context: Spring searches
// upward from the carrier for one. Declaring it here keeps the example self-contained in
// its own package (component scans must not cross-contaminate other suites).
@SpringBootConfiguration
class JsonSliceConfig

// A non-WebFlux slice. @JsonTest wires only the JSON infrastructure (a Jackson 3
// ObjectMapper, the tools.jackson.* namespace Spring Boot 4.1 moved to), proving the
// springTest bridge is slice-agnostic rather than WebFlux-specific.
@JsonTest
class JsonSliceContext : SpringTestConfig()

val JsonSliceSuite by testSuite {
    springTest<JsonSliceContext> {
        test("the @JsonTest slice provides a configured ObjectMapper") {
            val mapper = bean<ObjectMapper>()

            val json = mapper.writeValueAsString(Coordinate(52.52, 13.405))

            // Read back into a tree (no jackson-module-kotlin needed for a constructor-only
            // data class) to prove the slice's ObjectMapper both serializes and parses.
            val tree = mapper.readTree(json)
            check(tree.get("lat").doubleValue() == 52.52)
            check(tree.get("lon").doubleValue() == 13.405)
        }
    }
}
