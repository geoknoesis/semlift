package io.semlift

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JsonTransformTest {
    @Test
    fun `kotlin json transform applies changes`() {
        val input = JsonObject(mapOf("value" to JsonPrimitive(1)))
        val transform = jsonTransform { set("/value", 2) }
        val output = transform.apply(input)
        val expected = JsonObject(mapOf("value" to JsonPrimitive(2)))
        assertThat(output).isEqualTo(expected)
    }
}

