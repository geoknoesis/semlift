package io.semlift

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

sealed interface JsonOp {
    data class Set(val path: String, val value: JsonElement) : JsonOp
    data class Remove(val path: String) : JsonOp
    data class Move(val from: String, val to: String) : JsonOp
    data class Default(val path: String, val value: JsonElement) : JsonOp
    data class MapArray(val path: String, val transform: JsonTransform) : JsonOp
}

class JsonDslBuilder {
    private val ops = mutableListOf<JsonOp>()

    fun set(path: String, value: Any?) {
        ops += JsonOp.Set(path, jsonValue(value))
    }

    fun set(path: String, value: JsonElement) {
        ops += JsonOp.Set(path, value)
    }

    fun remove(path: String) {
        ops += JsonOp.Remove(path)
    }

    fun move(from: String, to: String) {
        ops += JsonOp.Move(from, to)
    }

    fun default(path: String, value: Any?) {
        ops += JsonOp.Default(path, jsonValue(value))
    }

    fun mapArray(path: String, block: JsonDslBuilder.() -> Unit) {
        val transform = jsonTransform(block)
        ops += JsonOp.MapArray(path, transform)
    }

    fun build(): JsonTransform {
        return JsonTransform { input ->
            ops.fold(input) { current, op -> applyOp(current, op) }
        }
    }
}

fun jsonTransform(block: JsonDslBuilder.() -> Unit): JsonTransform {
    return JsonDslBuilder().apply(block).build()
}

private fun applyOp(input: JsonElement, op: JsonOp): JsonElement {
    return when (op) {
        is JsonOp.Set -> setAt(input, op.path, op.value)
        is JsonOp.Remove -> removeAt(input, op.path)
        is JsonOp.Move -> {
            val value = getAt(input, op.from) ?: return input
            val removed = removeAt(input, op.from)
            setAt(removed, op.to, value)
        }
        is JsonOp.Default -> {
            val current = getAt(input, op.path)
            if (current == null || current is JsonNull) {
                setAt(input, op.path, op.value)
            } else {
                input
            }
        }
        is JsonOp.MapArray -> {
            val target = getAt(input, op.path)
            if (target is JsonArray) {
                val updated = buildJsonArray {
                    target.forEach { element ->
                        val next = op.transform.apply(element)
                        add(next)
                    }
                }
                setAt(input, op.path, updated)
            } else {
                input
            }
        }
    }
}

private fun getAt(element: JsonElement, path: String): JsonElement? {
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

private fun setAt(element: JsonElement, path: String, value: JsonElement): JsonElement {
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

private fun removeAt(element: JsonElement, path: String): JsonElement {
    val segments = parsePointer(path)
    if (segments.isEmpty()) return JsonNull
    return removeAtSegments(element, segments)
}

private fun removeAtSegments(element: JsonElement, segments: List<String>): JsonElement {
    val head = segments.first()
    val tail = segments.drop(1)
    return when (element) {
        is JsonObject -> {
            if (tail.isEmpty()) {
                buildJsonObject {
                    element.forEach { (key, value) ->
                        if (key != head) put(key, value)
                    }
                }
            } else {
                val child = element[head] ?: return element
                val updatedChild = removeAtSegments(child, tail)
                buildJsonObject {
                    element.forEach { (key, value) ->
                        if (key != head) put(key, value)
                    }
                    put(head, updatedChild)
                }
            }
        }
        is JsonArray -> {
            val index = head.toIntOrNull() ?: return element
            if (index !in element.indices) return element
            val items = element.toMutableList()
            if (tail.isEmpty()) {
                items[index] = JsonNull
            } else {
                items[index] = removeAtSegments(items[index], tail)
            }
            JsonArray(items)
        }
        else -> element
    }
}

private fun parsePointer(pointer: String): List<String> {
    if (pointer.isBlank() || pointer == "/") return emptyList()
    return pointer.trimStart('/').split('/').map { it.replace("~1", "/").replace("~0", "~") }
}

private fun jsonValue(value: Any?): JsonElement {
    return when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        else -> JsonPrimitive(value.toString())
    }
}

