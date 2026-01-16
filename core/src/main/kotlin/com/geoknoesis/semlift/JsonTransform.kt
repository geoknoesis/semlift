package io.semlift

import kotlinx.serialization.json.JsonElement

fun interface JsonTransform {
    fun apply(input: JsonElement): JsonElement
}

