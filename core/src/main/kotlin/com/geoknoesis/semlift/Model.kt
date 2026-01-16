package io.semlift

@JvmInline
value class JsonDocument(val bytes: ByteArray)

@JvmInline
value class RdfDocument(val bytes: ByteArray)

sealed class RdfOutput {
    data object Turtle : RdfOutput()
    data object JsonLd : RdfOutput()
    data object NTriples : RdfOutput()
}

data class ValidationReport(
    val conforms: Boolean,
    val reportRdf: RdfDocument
)

data class LiftResult(
    val rdf: RdfDocument,
    val report: ValidationReport? = null,
    val diagnostics: Diagnostics = Diagnostics()
)

data class Diagnostics(
    val appliedSteps: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

data class LiftOptions(
    val baseIri: String = "urn:base:",
    val output: RdfOutput = RdfOutput.Turtle,
    val strict: Boolean = true,
    val jqBinary: String = "jq",
    val contextOverride: ContextSpec? = null,
    val idRulesOverride: List<IdRule> = emptyList(),
    val csvInferTypes: Boolean = true,
    val jdbcFetchSize: Int = 1000,
    val sparkMaxRows: Int? = null
)

