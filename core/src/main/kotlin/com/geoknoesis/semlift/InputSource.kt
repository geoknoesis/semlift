package io.semlift

sealed interface InputSource {
    data class Json(val bytes: ByteArray) : InputSource

    data class Xml(val bytes: ByteArray) : InputSource

    data class Csv(
        val bytes: ByteArray,
        val hasHeader: Boolean = true
    ) : InputSource

    data class Jdbc(
        val jdbcUrl: String,
        val table: String,
        val user: String? = null,
        val password: String? = null,
        val query: String? = null
    ) : InputSource

    data class Spark(
        val table: String? = null,
        val query: String? = null
    ) : InputSource

    data class Api(
        val protocol: String,
        val config: ApiConfig
    ) : InputSource
}

