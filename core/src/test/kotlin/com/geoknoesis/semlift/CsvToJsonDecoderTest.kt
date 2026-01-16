package io.semlift

import io.semlift.jsonld.KotlinxJsonCodec
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CsvToJsonDecoderTest {
    @Test
    fun `decodes csv with header and type coercion`() = kotlinx.coroutines.runBlocking {
        val csv = resourceBytes("/samples/sample.csv")

        val decoder = CsvToJsonDecoder(inferTypes = true)
        val document = decoder.decode(InputSource.Csv(csv, hasHeader = true))
        val codec = KotlinxJsonCodec()
        val actual = codec.parse(document)

        val expected = JsonArray(
            listOf(
                JsonObject(
                    mapOf(
                        "id" to JsonPrimitive(1),
                        "active" to JsonPrimitive(true),
                        "score" to JsonPrimitive(3.5)
                    )
                ),
                JsonObject(
                    mapOf(
                        "id" to JsonPrimitive(2),
                        "active" to JsonPrimitive(false),
                        "score" to JsonPrimitive(4)
                    )
                )
            )
        )
        assertThat(actual).isEqualTo(expected)
    }

    private fun resourceBytes(path: String): ByteArray {
        return requireNotNull(javaClass.getResourceAsStream(path)) {
            "Missing test resource: $path"
        }.use { it.readBytes() }
    }
}

