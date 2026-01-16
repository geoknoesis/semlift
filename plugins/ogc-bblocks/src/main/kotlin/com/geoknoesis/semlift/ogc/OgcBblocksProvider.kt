package io.semlift.ogc

import com.fasterxml.jackson.databind.JsonNode
import io.semlift.CacheConfig
import io.semlift.CachingResourceResolver
import io.semlift.ContextSpec
import io.semlift.LiftPlan
import io.semlift.JsonSchemaValidator
import io.semlift.PlanProvider
import io.semlift.PreStep
import io.semlift.ResourceResolver

data class BuildingBlock(
    val itemIdentifier: String,
    val name: String,
    val group: String?,
    val scope: String?,
    val itemClass: String?,
    val ldContext: String?,
    val schema: SchemaLinks?,
    val documentation: DocumentationLinks?
) {
    data class SchemaLinks(
        val applicationJson: String?,
        val applicationYaml: String?
    )

    data class DocumentationLinks(
        val markdownUrl: String?,
        val jsonFullUrl: String?,
        val bblocksViewerUrl: String?
    )
}

data class ExampleValidationResult(
    val url: String,
    val expectedToFail: Boolean,
    val valid: Boolean,
    val errors: List<String>
)

data class BlockValidationSummary(
    val blockId: String,
    val total: Int,
    val passed: Int,
    val failed: Int,
    val results: List<ExampleValidationResult>
)

class OgcBblocksProvider(
    private val registryUrl: String = DEFAULT_REGISTRY_URL,
    private val resolver: ResourceResolver = CachingResourceResolver(
        config = CacheConfig()
    )
) : PlanProvider {
    override val id: String = "ogc-bblocks"

    override suspend fun resolve(identifier: String): LiftPlan {
        val block = findByIdentifier(identifier)
        val context = requireNotNull(block.ldContext) {
            "Building block lacks ldContext: ${block.itemIdentifier}"
        }
        val pre = mutableListOf<PreStep>()
        block.schema?.applicationJson?.let { schemaUrl ->
            val schemaBytes = resolver.resolve(schemaUrl)
            pre += PreStep.JsonSchema(schema = schemaBytes, strict = true)
        }
        return LiftPlan(
            context = ContextSpec.Resolved(context),
            pre = pre
        )
    }

    suspend fun listBlocks(): List<BuildingBlock> {
        val root = loadRegistry()
        val items = root.get("bblocks") ?: return emptyList()
        if (!items.isArray) {
            error("Registry bblocks is not an array")
        }
        return items.map { toBlock(it) }
    }

    suspend fun findByIdentifier(identifier: String): BuildingBlock {
        return listBlocks().firstOrNull {
            it.itemIdentifier.equals(identifier, ignoreCase = true)
        } ?: error("Building block not found: $identifier")
    }

    suspend fun findByNameContains(query: String): List<BuildingBlock> {
        return listBlocks().filter {
            it.name.contains(query, ignoreCase = true)
        }
    }

    suspend fun validateExamples(identifier: String, strict: Boolean = true): BlockValidationSummary {
        val block = findByIdentifier(identifier)
        val schemaUrl = block.schema?.applicationJson
            ?: error("Building block lacks JSON schema: ${block.itemIdentifier}")
        val schemaBytes = resolver.resolve(schemaUrl)
        val validator = JsonSchemaValidator()

        val reportUrl = loadRegistry().get("validationReportJson")?.asText()
            ?: error("Registry missing validationReportJson")
        val reportRoot = io.semlift.JacksonSupport.jsonMapper.readTree(resolver.resolve(reportUrl))
        val items = reportRoot.get("bblocks")?.get(block.itemIdentifier)?.get("items")

        val results = mutableListOf<ExampleValidationResult>()
        if (items != null && items.isArray) {
            items.forEach { item ->
                val source = item.get("source")
                val url = source?.get("url")?.asText()
                    ?: return@forEach
                val expectedToFail = source.get("requireFail")?.asBoolean() ?: false
                val exampleBytes = resolver.resolve(url)
                val result = validator.validate(schemaBytes, exampleBytes)
                val pass = if (expectedToFail) !result.valid else result.valid
                results += ExampleValidationResult(
                    url = url,
                    expectedToFail = expectedToFail,
                    valid = pass,
                    errors = result.errors
                )
            }
        }

        val passed = results.count { it.valid }
        val failed = results.size - passed
        if (strict && failed > 0) {
            error("Building block validation failed for ${block.itemIdentifier}: $failed/${results.size} examples")
        }

        return BlockValidationSummary(
            blockId = block.itemIdentifier,
            total = results.size,
            passed = passed,
            failed = failed,
            results = results
        )
    }

    private suspend fun loadRegistry(): JsonNode {
        val bytes = resolver.resolve(registryUrl)
        return io.semlift.JacksonSupport.jsonMapper.readTree(bytes)
    }

    private fun toBlock(node: JsonNode): BuildingBlock {
        val schemaLinks = node.get("schema")?.let { schemaNode ->
            BuildingBlock.SchemaLinks(
                applicationJson = schemaNode.get("application/json")?.asText(),
                applicationYaml = schemaNode.get("application/yaml")?.asText()
            )
        }
        val documentationLinks = node.get("documentation")?.let { docNode ->
            BuildingBlock.DocumentationLinks(
                markdownUrl = docNode.get("markdown")?.get("url")?.asText(),
                jsonFullUrl = docNode.get("json-full")?.get("url")?.asText(),
                bblocksViewerUrl = docNode.get("bblocks-viewer")?.get("url")?.asText()
            )
        }
        return BuildingBlock(
            itemIdentifier = node.get("itemIdentifier")?.asText()
                ?: error("Missing itemIdentifier"),
            name = node.get("name")?.asText() ?: "Unknown",
            group = node.get("group")?.asText(),
            scope = node.get("scope")?.asText(),
            itemClass = node.get("itemClass")?.asText(),
            ldContext = node.get("ldContext")?.asText(),
            schema = schemaLinks,
            documentation = documentationLinks
        )
    }

    companion object {
        const val DEFAULT_REGISTRY_URL = "https://opengeospatial.github.io/bblocks/register.json"
    }
}

