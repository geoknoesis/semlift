package io.semlift

fun interface JsonTransformFactory {
    fun create(): JsonTransform
}

