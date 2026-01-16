package io.semlift

import io.semlift.jsonld.KotlinxJsonCodec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class XmlToJsonDecoderTest {
    @Test
    fun `decodes xml with attributes and text nodes`() = kotlinx.coroutines.runBlocking {
        val xml = resourceBytes("/samples/sample.xml")

        val decoder = XmlToJsonDecoder()
        val document = decoder.decode(InputSource.Xml(xml))
        val codec = KotlinxJsonCodec()
        val actual = codec.parse(document)
        val expected = kotlinx.serialization.json.JsonObject(
            mapOf(
                "root" to JsonObject(
                    mapOf(
                        "@id" to JsonPrimitive("1"),
                        "name" to JsonPrimitive("Alpha"),
                        "value" to JsonObject(
                            mapOf(
                                "@unit" to JsonPrimitive("kg"),
                                "#text" to JsonPrimitive("10")
                            )
                        )
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

