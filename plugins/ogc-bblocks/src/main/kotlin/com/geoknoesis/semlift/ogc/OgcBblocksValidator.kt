package io.semlift.ogc

import io.semlift.CacheConfig
import io.semlift.CachingResourceResolver
import io.semlift.ContextSpec
import io.semlift.DefaultSemanticLifter
import io.semlift.JsonDocument
import io.semlift.JsonSchemaToShacl
import io.semlift.JsonSchemaValidator
import io.semlift.LiftOptions
import io.semlift.RdfOutput
import io.semlift.ResourceResolver
import io.semlift.jena.JenaBackend
import io.semlift.jena.JenaJsonLdToRdf
import io.semlift.jsonld.KotlinxJsonCodec
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant

private const val DEFAULT_OUTPUT_DIR = "build/ogc-bblocks"

fun main(args: Array<String>) = runBlocking {
    val outputDir = if (args.isNotEmpty()) Paths.get(args[0]) else Paths.get(DEFAULT_OUTPUT_DIR)
    val resolver = CachingResourceResolver(config = CacheConfig())
    val provider = OgcBblocksProvider(resolver = resolver)
    val validator = OgcBblocksValidator(resolver, provider)
    validator.validateAll(outputDir)
}

class OgcBblocksValidator(
    private val resolver: ResourceResolver,
    private val provider: OgcBblocksProvider
) {
    suspend fun validateAll(outputDir: Path) {
        Files.createDirectories(outputDir)
        val registry = provider.listBlocks().associateBy { it.itemIdentifier }
        val reportRoot = io.semlift.JacksonSupport.jsonMapper.readTree(loadReportJson())
        val bblocks = reportRoot.get("bblocks") ?: return

        val blockReports = mutableListOf<BlockReport>()

        bblocks.fields().forEach { (blockId, blockReport) ->
            val block = registry[blockId]
            val items = blockReport.get("items")
            if (items == null || !items.isArray || items.size() == 0) {
                println("[$blockId] no examples")
                blockReports += BlockReport(
                    blockId = blockId,
                    blockName = block?.name ?: blockReport.get("bblockName")?.asText() ?: blockId,
                    total = 0,
                    passed = 0,
                    failed = 0,
                    examples = emptyList()
                )
                return@forEach
            }
            val exampleReports = mutableListOf<ExampleReport>()
            items.forEachIndexed { index, item ->
                val source = item.get("source")
                val url = source?.get("url")?.asText() ?: return@forEachIndexed
                val filename = source.get("filename")?.asText()
                    ?: url.substringAfterLast("/")
                val safeName = Paths.get(filename).fileName.toString()
                val expectedToFail = source.get("requireFail")?.asBoolean() ?: false
                val exampleBytes = resolver.resolve(url)
                val exampleDir = outputDir.resolve(blockId).also { Files.createDirectories(it) }

                val inputPath = exampleDir.resolve("$safeName.input.json")
                Files.write(inputPath, exampleBytes)

                var schemaPassed: Boolean? = null
                var schemaMessage: String? = null
                var schemaShaclPath: Path? = null
                val schemaUrl = block?.schema?.applicationJson
                val contextUrl = block?.ldContext
                val contextBytes = contextUrl?.let { resolver.resolve(it) }
                if (schemaUrl != null) {
                    val schemaBytes = resolver.resolve(schemaUrl)
                    val validator = JsonSchemaValidator()
                    val result = validator.validate(schemaBytes, exampleBytes)
                    val effective = if (!result.valid) {
                        validateArrayElements(schemaBytes, exampleBytes, validator) ?: result
                    } else {
                        result
                    }
                    schemaPassed = if (expectedToFail) !effective.valid else effective.valid
                    val validationPath = exampleDir.resolve("$safeName.validation.txt")
                    val content = if (effective.valid) {
                        "Validation passed"
                    } else {
                        effective.errors.joinToString(System.lineSeparator())
                    }
                    Files.writeString(validationPath, content)
                    schemaMessage = content

                    val shaclGenerator = JsonSchemaToShacl()
                    val shaclTtl = shaclGenerator.generate(schemaBytes, contextBytes, JenaBackend())
                    schemaShaclPath = exampleDir.resolve("$safeName.schema.shacl.ttl")
                    Files.writeString(schemaShaclPath, shaclTtl)
                }

                var jsonLdPath: Path? = null
                var ttlPath: Path? = null
                var liftError: String? = null
                if (contextBytes != null) {
                    val strippedExample = stripExistingContext(exampleBytes)
                    val liftInput = normalizeForNest(strippedExample)
                    val jsonLdBytes = embedContext(liftInput, contextBytes)
                    jsonLdPath = exampleDir.resolve("$safeName.context.jsonld")
                    Files.write(jsonLdPath, jsonLdBytes)

                    val lifter = DefaultSemanticLifter(
                        json = KotlinxJsonCodec(),
                        resolver = resolver,
                        jsonLd = JenaJsonLdToRdf(),
                        rdf = JenaBackend()
                    )
                    try {
                        val result = lifter.lift(
                            io.semlift.InputSource.Json(liftInput),
                            io.semlift.LiftPlan(context = ContextSpec.Inline(contextBytes)),
                            LiftOptions(output = RdfOutput.JsonLd, baseIri = "urn:base:")
                        )
                        val ttlBytes = jsonLdToTurtle(result.rdf.bytes)
                        ttlPath = exampleDir.resolve("$safeName.ttl")
                        Files.write(ttlPath, ttlBytes)
                    } catch (err: Exception) {
                        liftError = err.message ?: err.toString()
                    }
                }

                val passed = listOfNotNull(schemaPassed, liftError == null).all { it }
                exampleReports += ExampleReport(
                    filename = safeName,
                    sourceUrl = url,
                    expectedToFail = expectedToFail,
                    inputPath = inputPath,
                    schemaPassed = schemaPassed,
                    schemaMessage = schemaMessage,
                    schemaShaclPath = schemaShaclPath,
                    jsonLdPath = jsonLdPath,
                    ttlPath = ttlPath,
                    liftError = liftError,
                    passed = passed
                )

                println("[$blockId][$index] input=$inputPath")
            }
            val passed = exampleReports.count { it.passed }
            val failed = exampleReports.size - passed
            blockReports += BlockReport(
                blockId = blockId,
                blockName = block?.name ?: blockReport.get("bblockName")?.asText() ?: blockId,
                total = exampleReports.size,
                passed = passed,
                failed = failed,
                examples = exampleReports
            )
        }

        writeHtmlReport(outputDir, blockReports)
        writeJsonReport(outputDir, blockReports)
    }

    private suspend fun loadReportJson(): ByteArray {
        val registryBytes = resolver.resolve(OgcBblocksProvider.DEFAULT_REGISTRY_URL)
        val node = io.semlift.JacksonSupport.jsonMapper.readTree(registryBytes)
        val reportUrl = node.get("validationReportJson")?.asText()
            ?: error("Registry missing validationReportJson")
        return resolver.resolve(reportUrl)
    }

    private fun embedContext(jsonBytes: ByteArray, contextBytes: ByteArray): ByteArray {
        val json = KotlinxJsonCodec()
        val payload = json.parse(JsonDocument(jsonBytes))
        val context = normalizeContext(json.parse(JsonDocument(contextBytes)))
        val merged = when (payload) {
            is JsonObject -> JsonObject(mapOf("@context" to context) + payload)
            is JsonArray -> JsonObject(mapOf("@context" to context, "@graph" to payload))
            else -> JsonObject(mapOf("@context" to context, "@value" to payload))
        }
        return json.encode(merged).bytes
    }

    private fun normalizeContext(context: kotlinx.serialization.json.JsonElement): kotlinx.serialization.json.JsonElement {
        return if (context is JsonObject && context.containsKey("@context")) {
            context["@context"] ?: context
        } else {
            context
        }
    }

    private fun stripExistingContext(jsonBytes: ByteArray): ByteArray {
        val json = KotlinxJsonCodec()
        val payload = json.parse(JsonDocument(jsonBytes))
        if (payload is JsonObject && payload.containsKey("@context")) {
            val filtered = JsonObject(payload.filterKeys { it != "@context" })
            return json.encode(filtered).bytes
        }
        return jsonBytes
    }

    private fun normalizeForNest(jsonBytes: ByteArray): ByteArray {
        val json = KotlinxJsonCodec()
        val payload = json.parse(JsonDocument(jsonBytes))
        val normalized = normalizeForNest(payload)
        return json.encode(normalized).bytes
    }

    private fun normalizeForNest(element: JsonElement): JsonElement {
        return when (element) {
            is JsonObject -> {
                val mapped = element.mapValues { (key, value) ->
                    if (key == "properties" && value is JsonNull) {
                        JsonObject(emptyMap())
                    } else {
                        normalizeForNest(value)
                    }
                }
                JsonObject(mapped)
            }
            is JsonArray -> JsonArray(element.map { normalizeForNest(it) })
            else -> element
        }
    }

    private fun validateArrayElements(
        schemaBytes: ByteArray,
        exampleBytes: ByteArray,
        validator: JsonSchemaValidator
    ): io.semlift.JsonSchemaValidationResult? {
        val node = io.semlift.JacksonSupport.jsonMapper.readTree(exampleBytes)
        if (!node.isArray) {
            return null
        }
        val errors = mutableListOf<String>()
        node.forEachIndexed { index, child ->
            val childBytes = io.semlift.JacksonSupport.jsonMapper.writeValueAsBytes(child)
            val result = validator.validate(schemaBytes, childBytes)
            if (!result.valid) {
                result.errors.forEach { errors += "[$index] $it" }
            }
        }
        return if (errors.isEmpty()) {
            io.semlift.JsonSchemaValidationResult(valid = true, errors = emptyList())
        } else {
            io.semlift.JsonSchemaValidationResult(valid = false, errors = errors)
        }
    }

    private fun jsonLdToTurtle(jsonLdBytes: ByteArray): ByteArray {
        val model = org.apache.jena.rdf.model.ModelFactory.createDefaultModel()
        org.apache.jena.riot.RDFParser.source(java.io.ByteArrayInputStream(jsonLdBytes))
            .lang(org.apache.jena.riot.Lang.JSONLD)
            .parse(model)
        val output = java.io.ByteArrayOutputStream()
        org.apache.jena.riot.RDFDataMgr.write(output, model, org.apache.jena.riot.Lang.TURTLE)
        return output.toByteArray()
    }

    private fun writeHtmlReport(outputDir: Path, reports: List<BlockReport>) {
        val html = buildString {
            appendLine("<!doctype html>")
            appendLine("<html><head><meta charset=\"utf-8\"><title>OGC Building Blocks Report</title></head><body>")
            appendLine("<h1>Building blocks validation report</h1>")
            appendLine("<p>Generated at ${Instant.now()}</p>")
            appendLine("<p>Number of passing building blocks: ${reports.count { it.failed == 0 }} / ${reports.size}</p>")
            appendLine("<p>Example totals: ${reports.sumOf { it.passed }} passed / ${reports.sumOf { it.total }} total</p>")
            reports.forEach { report ->
                appendLine("<h2>${report.blockName} (${report.blockId})</h2>")
                if (report.total == 0) {
                    appendLine("<p>No tests were found for this building block.</p>")
                } else {
                    appendLine("<p>Test passed: ${report.passed} / ${report.total}</p>")
                    report.examples.forEach { example ->
                        appendLine("<h3>${example.filename}</h3>")
                        appendLine("<ul>")
                        appendLine("<li>Source: <a href=\"${example.sourceUrl}\">${example.sourceUrl}</a></li>")
                        appendLine("<li>Input: ${example.inputPath}</li>")
                        example.schemaPassed?.let {
                            appendLine("<li>JSON Schema: ${if (it) "passed" else "failed"}</li>")
                        }
                        example.schemaShaclPath?.let { appendLine("<li>JSON Schema SHACL: $it</li>") }
                        example.jsonLdPath?.let { appendLine("<li>JSON-LD: $it</li>") }
                        example.ttlPath?.let { appendLine("<li>Turtle: $it</li>") }
                        example.liftError?.let { appendLine("<li>Lift error: $it</li>") }
                        appendLine("</ul>")
                    }
                }
            }
            appendLine("</body></html>")
        }
        Files.writeString(outputDir.resolve("report.html"), html)
    }

    private fun writeJsonReport(outputDir: Path, reports: List<BlockReport>) {
        val out = mapOf(
            "generated" to Instant.now().toString(),
            "summary" to mapOf(
                "total" to reports.sumOf { it.total },
                "passed" to reports.sumOf { it.passed },
                "failed" to reports.sumOf { it.failed }
            ),
            "blocks" to reports
        )
        Files.write(
            outputDir.resolve("report.json"),
            io.semlift.JacksonSupport.jsonMapper.writeValueAsBytes(out)
        )
    }
}

data class ExampleReport(
    val filename: String,
    val sourceUrl: String,
    val expectedToFail: Boolean,
    val inputPath: Path,
    val schemaPassed: Boolean?,
    val schemaMessage: String?,
    val schemaShaclPath: Path?,
    val jsonLdPath: Path?,
    val ttlPath: Path?,
    val liftError: String?,
    val passed: Boolean
)

data class BlockReport(
    val blockId: String,
    val blockName: String,
    val total: Int,
    val passed: Int,
    val failed: Int,
    val examples: List<ExampleReport>
)

