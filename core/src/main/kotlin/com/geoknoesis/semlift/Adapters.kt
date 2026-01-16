package io.semlift

import kotlinx.serialization.json.JsonElement

interface JsonCodec {
    fun parse(doc: JsonDocument): JsonElement
    fun encode(elem: JsonElement): JsonDocument
}

interface JsonLdToRdf {
    fun toDataset(jsonLd: ByteArray, baseIri: String): RdfDataset
}

interface RdfBackend {
    fun serialize(dataset: RdfDataset, out: RdfOutput): RdfDocument
    fun serializeGraph(graph: RdfGraph, out: RdfOutput): RdfDocument
    fun shaclValidate(dataset: RdfDataset, shapesTtl: ByteArray): ValidationReport
    fun sparqlConstruct(dataset: RdfDataset, query: String): RdfDataset
    fun sparqlUpdate(dataset: RdfDataset, update: String): RdfDataset
}

interface RdfDataset

interface ResourceResolver {
    suspend fun resolve(uri: String): ByteArray
}

interface JqRunner {
    fun apply(program: String, input: ByteArray): ByteArray
}

