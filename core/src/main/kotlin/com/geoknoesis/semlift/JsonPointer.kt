package io.semlift

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

internal fun getAt(element: JsonElement, path: String): JsonElement? {
    val segments = parsePointer(path)
    var current: JsonElement = element
    for (segment in segments) {
        current = when (current) {
            is JsonObject -> current[segment] ?: return null
            is JsonArray -> {
                val index = segment.toIntOrNull() ?: return null
                current.getOrNull(index) ?: return null
            }
            else -> return null
        }
    }
    return current
}

internal fun setAt(element: JsonElement, path: String, value: JsonElement): JsonElement {
    val segments = parsePointer(path)
    if (segments.isEmpty()) return value
    return setAtSegments(element, segments, value)
}

private fun setAtSegments(element: JsonElement, segments: List<String>, value: JsonElement): JsonElement {
    val head = segments.first()
    val tail = segments.drop(1)
    return when (element) {
        is JsonObject -> {
            val updated = if (tail.isEmpty()) {
                value
            } else {
                val child = element[head] ?: JsonObject(emptyMap())
                setAtSegments(child, tail, value)
            }
            buildJsonObject {
                element.forEach { (key, existing) ->
                    if (key != head) put(key, existing)
                }
                put(head, updated)
            }
        }
        is JsonArray -> {
            val index = head.toIntOrNull() ?: return element
            val items = element.toMutableList()
            while (items.size <= index) {
                items.add(JsonNull)
            }
            items[index] = if (tail.isEmpty()) value else setAtSegments(items[index], tail, value)
            JsonArray(items)
        }
        else -> {
            if (head.toIntOrNull() != null) {
                val array = MutableList<JsonElement>(head.toInt() + 1) { JsonNull }
                array[head.toInt()] = if (tail.isEmpty()) value else setAtSegments(JsonNull, tail, value)
                JsonArray(array)
            } else {
                buildJsonObject {
                    put(head, if (tail.isEmpty()) value else setAtSegments(JsonObject(emptyMap()), tail, value))
                }
            }
        }
    }
}

private fun parsePointer(pointer: String): List<String> {
    if (pointer.isBlank() || pointer == "/") return emptyList()
    return pointer.trimStart('/').split('/').map { it.replace("~1", "/").replace("~0", "~") }
}

