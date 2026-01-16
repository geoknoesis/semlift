package io.semlift.jsonld

import io.semlift.JsonCodec
import io.semlift.JsonDocument
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

class KotlinxJsonCodec(
    private val json: Json = Json { ignoreUnknownKeys = true }
) : JsonCodec {
    override fun parse(doc: JsonDocument): JsonElement {
        return json.parseToJsonElement(doc.bytes.decodeToString())
    }

    override fun encode(elem: JsonElement): JsonDocument {
        return JsonDocument(json.encodeToString(JsonElement.serializer(), elem).toByteArray())
    }
}

