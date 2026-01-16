package io.semlift

fun interface SyntaxDecoder {
    suspend fun decode(source: InputSource): JsonDocument
}

