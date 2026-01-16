package io.semlift

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import org.yaml.snakeyaml.Yaml
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

sealed interface ApiConfig {
    data class OgcApiFeatures(
        val baseUrl: String,
        val collection: String,
        val limit: Int? = null,
        val bbox: String? = null,
        val params: Map<String, String> = emptyMap(),
        val headers: Map<String, String> = emptyMap(),
        val recordPath: String? = null
    ) : ApiConfig

    data class Wfs(
        val baseUrl: String,
        val typeName: String,
        val version: String = "2.0.0",
        val outputFormat: String? = "application/json",
        val srsName: String? = null,
        val pageSize: Int? = null,
        val startIndex: Int? = null,
        val params: Map<String, String> = emptyMap(),
        val headers: Map<String, String> = emptyMap(),
        val recordPath: String? = null
    ) : ApiConfig

    data class OpenApi(
        val spec: String,
        val operationId: String? = null,
        val path: String? = null,
        val method: String? = null,
        val server: String? = null,
        val params: Map<String, String> = emptyMap(),
        val headers: Map<String, String> = emptyMap(),
        val body: String? = null,
        val recordPath: String? = null
    ) : ApiConfig

    data class Custom(val entries: Map<String, Any?>) : ApiConfig
}

interface ApiProtocol {
    val id: String
    suspend fun fetch(source: InputSource.Api, resolver: ResourceResolver): JsonDocument
}

interface ApiProtocolRegistry {
    fun protocolFor(id: String): ApiProtocol
    fun register(protocol: ApiProtocol)
}

class DefaultApiProtocolRegistry(
    protocols: List<ApiProtocol> = listOf(
        OgcApiFeaturesProtocol(),
        WfsProtocol(),
        OpenApiProtocol()
    )
) : ApiProtocolRegistry {
    private val registry = protocols.associateBy { it.id.lowercase() }.toMutableMap()

    override fun protocolFor(id: String): ApiProtocol {
        return registry[id.lowercase()] ?: error("Unsupported API protocol: $id")
    }

    override fun register(protocol: ApiProtocol) {
        registry[protocol.id.lowercase()] = protocol
    }
}

class OgcApiFeaturesProtocol : ApiProtocol {
    override val id: String = "ogc-api-features"

    override suspend fun fetch(source: InputSource.Api, resolver: ResourceResolver): JsonDocument {
        val config = source.config as? ApiConfig.OgcApiFeatures
            ?: error("OGC API Features protocol requires OgcApiFeatures config")

        val mapper = JacksonSupport.jsonMapper
        val output = mapper.nodeFactory.arrayNode()

        var nextUrl: String? = ogcItemsUrl(config)
        while (nextUrl != null) {
            val response = httpRequest("GET", nextUrl, config.headers)
            val node = mapper.readTree(response.bytes)
            val records = selectRecords(
                node,
                config.recordPath ?: "features"
            )
            appendRecords(output, records)
            nextUrl = findNextLink(node)
        }

        return JsonDocument(mapper.writeValueAsBytes(output))
    }

    private fun ogcItemsUrl(config: ApiConfig.OgcApiFeatures): String {
        val base = config.baseUrl.trimEnd('/')
        val collection = config.collection.trim('/')
        val url = "$base/collections/$collection/items"
        val params = mutableMapOf<String, String>()
        config.limit?.let { params["limit"] = it.toString() }
        config.bbox?.let { params["bbox"] = it }
        params.putAll(config.params)
        return appendQueryParams(url, params)
    }

    private fun findNextLink(node: JsonNode): String? {
        val links = node.get("links")
        if (links != null && links.isArray) {
            links.forEach { link ->
                val rel = link.get("rel")?.asText()?.lowercase(Locale.US)
                if (rel == "next") {
                    return link.get("href")?.asText()
                }
            }
        }
        return node.get("next")?.asText()
    }
}

class WfsProtocol : ApiProtocol {
    override val id: String = "wfs"

    override suspend fun fetch(source: InputSource.Api, resolver: ResourceResolver): JsonDocument {
        val config = source.config as? ApiConfig.Wfs
            ?: error("WFS protocol requires Wfs config")

        val mapper = JacksonSupport.jsonMapper
        val output = mapper.nodeFactory.arrayNode()

        var startIndex = config.startIndex ?: 0
        var continuePaging = true
        val pageSize = config.pageSize

        while (continuePaging) {
            val url = wfsUrl(config, startIndex)
            val response = httpRequest("GET", url, config.headers)
            val node = parseJsonOrXml(response.bytes)
            val records = selectWfsRecords(node, config.recordPath)
            appendRecords(output, records)

            if (pageSize == null || records.size() < pageSize) {
                continuePaging = false
            } else {
                startIndex += pageSize
            }
        }

        return JsonDocument(mapper.writeValueAsBytes(output))
    }

    private fun wfsUrl(config: ApiConfig.Wfs, startIndex: Int): String {
        val params = mutableMapOf(
            "service" to "WFS",
            "request" to "GetFeature",
            "version" to config.version,
            "typeName" to config.typeName
        )
        config.outputFormat?.let { params["outputFormat"] = it }
        config.srsName?.let { params["srsName"] = it }
        config.pageSize?.let { params["count"] = it.toString() }
        params["startIndex"] = startIndex.toString()
        params.putAll(config.params)
        return appendQueryParams(config.baseUrl, params)
    }
}

class OpenApiProtocol : ApiProtocol {
    override val id: String = "openapi"

    override suspend fun fetch(source: InputSource.Api, resolver: ResourceResolver): JsonDocument {
        val config = source.config as? ApiConfig.OpenApi
            ?: error("OpenAPI protocol requires OpenApi config")

        val specRoot = loadSpec(config.spec, resolver)
        val (path, method) = resolveOperation(specRoot, config)
        val baseUrl = resolveServer(specRoot, config)
        val (resolvedPath, remainingParams) = substitutePathParams(path, config.params)

        val url = appendQueryParams(baseUrl.trimEnd('/') + resolvedPath, remainingParams)
        val response = httpRequest(
            method,
            url,
            headers = config.headers,
            body = config.body?.toByteArray(StandardCharsets.UTF_8)
        )

        val mapper = JacksonSupport.jsonMapper
        val node = mapper.readTree(response.bytes)
        val records = selectRecords(node, config.recordPath)
        return JsonDocument(mapper.writeValueAsBytes(records))
    }

    private suspend fun loadSpec(specRef: String, resolver: ResourceResolver): JsonNode {
        val bytes = resolver.resolve(specRef)
        return parseJsonOrYaml(bytes)
    }

    private fun resolveOperation(specRoot: JsonNode, config: ApiConfig.OpenApi): Pair<String, String> {
        val method = config.method?.lowercase(Locale.US)
        if (config.path != null && method != null) {
            return config.path to method
        }

        val operationId = config.operationId ?: error("OpenAPI requires operationId or path+method")
        val paths = specRoot.get("paths") ?: error("OpenAPI spec missing paths")
        val fields = paths.fields()
        while (fields.hasNext()) {
            val entry = fields.next()
            val path = entry.key
            val methods = entry.value.fields()
            while (methods.hasNext()) {
                val methodEntry = methods.next()
                val op = methodEntry.value
                if (op.get("operationId")?.asText() == operationId) {
                    return path to methodEntry.key.lowercase(Locale.US)
                }
            }
        }
        error("OpenAPI operationId not found: $operationId")
    }

    private fun resolveServer(specRoot: JsonNode, config: ApiConfig.OpenApi): String {
        config.server?.let { return it }
        val servers = specRoot.get("servers")
        if (servers != null && servers.isArray && servers.size() > 0) {
            val url = servers[0].get("url")?.asText()
            if (!url.isNullOrBlank()) {
                return url
            }
        }
        error("OpenAPI server URL missing; set config.server or spec servers[0].url")
    }
}

private data class HttpResponse(
    val status: Int,
    val bytes: ByteArray,
    val headers: Map<String, List<String>>
)

private fun httpRequest(
    method: String,
    url: String,
    headers: Map<String, String>,
    body: ByteArray? = null
): HttpResponse {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.requestMethod = method.uppercase(Locale.US)
    headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
    connection.doInput = true
    if (body != null) {
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write(body) }
    }

    val status = connection.responseCode
    val stream = if (status in 200..299) {
        connection.inputStream
    } else {
        connection.errorStream
    }
    val bytes = stream?.readBytes() ?: ByteArray(0)
    if (status !in 200..299) {
        error("HTTP $status from $url: ${bytes.decodeToString()}")
    }
    return HttpResponse(status, bytes, connection.headerFields.filterKeys { it != null })
}

private fun appendQueryParams(url: String, params: Map<String, String>): String {
    if (params.isEmpty()) {
        return url
    }
    val encoded = params.entries.joinToString("&") { (key, value) ->
        "${encode(key)}=${encode(value)}"
    }
    val separator = if (url.contains("?")) "&" else "?"
    return "$url$separator$encoded"
}

private fun encode(value: String): String {
    return URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}

private fun selectRecords(node: JsonNode, recordPath: String?): JsonNode {
    if (recordPath.isNullOrBlank()) {
        return node
    }
    val pointer = if (recordPath.startsWith("/")) {
        recordPath
    } else {
        "/" + recordPath.split(".").joinToString("/")
    }
    val selected = node.at(pointer)
    if (selected.isMissingNode) {
        error("Record path not found: $recordPath")
    }
    return selected
}

private fun appendRecords(target: ArrayNode, records: JsonNode) {
    when {
        records.isArray -> records.forEach { target.add(it) }
        records.isObject -> target.add(records)
        records.isMissingNode -> Unit
        else -> target.add(records)
    }
}

private fun selectWfsRecords(node: JsonNode, recordPath: String?): JsonNode {
    recordPath?.let { return selectRecords(node, it) }
    if (node.get("features")?.isArray == true) {
        return node.get("features")
    }
    val members = collectMembers(node, listOf("featureMember", "member"))
    if (members.isArray && members.size() > 0) {
        return members
    }
    return node
}

private fun collectMembers(node: JsonNode, keys: List<String>): ArrayNode {
    val mapper = JacksonSupport.jsonMapper
    val output = mapper.nodeFactory.arrayNode()
    collectMembers(node, keys, output)
    return output
}

private fun collectMembers(node: JsonNode, keys: List<String>, output: ArrayNode) {
    if (node.isObject) {
        keys.forEach { key ->
            val value = node.get(key)
            if (value != null) {
                if (value.isArray) {
                    value.forEach { output.add(it) }
                } else {
                    output.add(value)
                }
            }
        }
        val fields = node.fields()
        while (fields.hasNext()) {
            val entry = fields.next()
            collectMembers(entry.value, keys, output)
        }
    } else if (node.isArray) {
        node.forEach { collectMembers(it, keys, output) }
    }
}

private fun parseJsonOrXml(bytes: ByteArray): JsonNode {
    val mapper = JacksonSupport.jsonMapper
    return runCatching { mapper.readTree(bytes) }.getOrElse {
        JacksonSupport.xmlMapper.readTree(bytes)
    }
}

private fun parseJsonOrYaml(bytes: ByteArray): JsonNode {
    val mapper = JacksonSupport.jsonMapper
    val trimmed = bytes.toString(StandardCharsets.UTF_8).trimStart()
    return if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
        mapper.readTree(bytes)
    } else {
        val yaml = Yaml()
        val loaded = yaml.load<Any>(trimmed) ?: emptyMap<String, Any>()
        mapper.valueToTree(loaded)
    }
}

private fun substitutePathParams(
    path: String,
    params: Map<String, String>
): Pair<String, Map<String, String>> {
    val remaining = params.toMutableMap()
    val resolved = Regex("\\{([^}]+)\\}").replace(path) { match ->
        val key = match.groupValues[1]
        val value = remaining.remove(key)
            ?: error("Missing path param: $key")
        encode(value)
    }
    return resolved to remaining
}

