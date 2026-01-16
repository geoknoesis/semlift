package io.semlift

import com.fasterxml.jackson.databind.JsonNode
import java.math.BigDecimal

data class ShaclConfig(
    val targetNamespace: String = "urn:semlift:shape#",
    val propertyNamespace: String? = null,
    val targetClass: String? = null,
    val shapeName: String? = null,
    val includeLabels: Boolean = true
)

class JsonSchemaToShacl(
    private val config: ShaclConfig = ShaclConfig()
) {
    private val propertyNamespace = config.propertyNamespace ?: config.targetNamespace
    private var bnodeCounter = 0

    fun generate(
        schemaBytes: ByteArray,
        contextBytes: ByteArray? = null,
        backend: RdfBackend? = null
    ): String {
        val schema = JacksonSupport.jsonMapper.readTree(schemaBytes)
        val contextMap = contextBytes?.let { parseContext(it) } ?: emptyMap()
        val contextPrefixes = contextBytes?.let { parseContextPrefixes(it) } ?: emptyMap()
        val shapeId = "${config.targetNamespace}${sanitize(config.shapeName ?: schema.get("title")?.asText() ?: "Root")}"
        val graph = GraphBuilder(
            prefixes = mapOf(
                "sh" to SHACL,
                "xsd" to XSD,
                "rdf" to RDF,
                "semlift" to config.targetNamespace
            ) + contextPrefixes
        )
        buildNodeShape(iri(shapeId), schema, contextMap, graph)
        val rdfGraph = graph.build()
        val turtle = backend?.serializeGraph(rdfGraph, RdfOutput.Turtle)?.bytes?.decodeToString()
        return turtle ?: RdfGraphTurtleWriter().write(rdfGraph)
    }

    private fun buildNodeShape(
        shapeId: RdfTerm.Iri,
        schema: JsonNode,
        contextMap: Map<String, String>,
        graph: GraphBuilder
    ) {
        val required = requiredSet(schema)
        val properties = propertiesMap(schema)
        graph.addType(shapeId, SHACL_NODE_SHAPE)
        if (config.targetClass != null) {
            graph.add(shapeId, SHACL_TARGET_CLASS, iri(config.targetClass))
        }
        if (config.includeLabels) {
            graph.add(shapeId, SHACL_LABEL, literal(shapeId.value.substringAfterLast('#')))
        }
        properties.forEach { (name, node) ->
            val propShapeId = bnode()
            graph.add(shapeId, SHACL_PROPERTY, propShapeId)
            graph.addType(propShapeId, SHACL_PROPERTY_SHAPE)
            graph.add(propShapeId, SHACL_PATH, iri(resolvePath(name, contextMap)))
            if (required.contains(name)) {
                graph.add(propShapeId, SHACL_MIN_COUNT, intLiteral(1))
            }
            applyConstraints(propShapeId, node, contextMap, graph, name)
        }
    }

    private fun applyConstraints(
        propShapeId: RdfTerm,
        schema: JsonNode,
        contextMap: Map<String, String>,
        graph: GraphBuilder,
        propName: String
    ) {
        val type = schema.get("type")?.asText()
        val enumNode = schema.get("enum")
        if (enumNode != null && enumNode.isArray) {
            val values = enumNode.map { enumLiteral(it) }
            val listHead = graph.addList(values)
            graph.add(propShapeId, SHACL_IN, listHead)
        }
        val pattern = schema.get("pattern")?.asText()
        if (pattern != null) {
            graph.add(propShapeId, SHACL_PATTERN, literal(pattern))
        }
        schema.get("minLength")?.asInt()?.let { graph.add(propShapeId, SHACL_MIN_LENGTH, intLiteral(it)) }
        schema.get("maxLength")?.asInt()?.let { graph.add(propShapeId, SHACL_MAX_LENGTH, intLiteral(it)) }
        schema.get("minimum")?.asText()?.let { graph.add(propShapeId, SHACL_MIN_INCLUSIVE, numericLiteral(it)) }
        schema.get("maximum")?.asText()?.let { graph.add(propShapeId, SHACL_MAX_INCLUSIVE, numericLiteral(it)) }
        schema.get("exclusiveMinimum")?.asText()?.let { graph.add(propShapeId, SHACL_MIN_EXCLUSIVE, numericLiteral(it)) }
        schema.get("exclusiveMaximum")?.asText()?.let { graph.add(propShapeId, SHACL_MAX_EXCLUSIVE, numericLiteral(it)) }

        when (type) {
            "string" -> graph.add(propShapeId, SHACL_DATATYPE, iri("${XSD}string"))
            "integer" -> graph.add(propShapeId, SHACL_DATATYPE, iri("${XSD}integer"))
            "number" -> graph.add(propShapeId, SHACL_DATATYPE, iri("${XSD}decimal"))
            "boolean" -> graph.add(propShapeId, SHACL_DATATYPE, iri("${XSD}boolean"))
            "object" -> {
                val nestedId = iri("${config.targetNamespace}${sanitize(propName)}Shape")
                graph.add(propShapeId, SHACL_NODE, nestedId)
                buildNodeShape(nestedId, schema, contextMap, graph)
            }
            "array" -> {
                schema.get("minItems")?.asInt()?.let { graph.add(propShapeId, SHACL_MIN_COUNT, intLiteral(it)) }
                schema.get("maxItems")?.asInt()?.let { graph.add(propShapeId, SHACL_MAX_COUNT, intLiteral(it)) }
                val items = schema.get("items")
                if (items != null) {
                    val itemType = items.get("type")?.asText()
                    when (itemType) {
                        "string" -> graph.add(propShapeId, SHACL_DATATYPE, iri("${XSD}string"))
                        "integer" -> graph.add(propShapeId, SHACL_DATATYPE, iri("${XSD}integer"))
                        "number" -> graph.add(propShapeId, SHACL_DATATYPE, iri("${XSD}decimal"))
                        "boolean" -> graph.add(propShapeId, SHACL_DATATYPE, iri("${XSD}boolean"))
                        "object" -> {
                            val nestedId = iri("${config.targetNamespace}${sanitize(propName)}Item")
                            graph.add(propShapeId, SHACL_NODE, nestedId)
                            buildNodeShape(nestedId, items, contextMap, graph)
                        }
                    }
                }
            }
        }
    }

    private fun propertiesMap(schema: JsonNode): Map<String, JsonNode> {
        val props = mutableMapOf<String, JsonNode>()
        schema.get("properties")?.fields()?.forEach { (name, node) ->
            props[name] = node
        }
        schema.get("allOf")?.forEach { sub ->
            sub.get("properties")?.fields()?.forEach { (name, node) ->
                props[name] = node
            }
        }
        return props
    }

    private fun requiredSet(schema: JsonNode): Set<String> {
        val required = mutableSetOf<String>()
        schema.get("required")?.forEach { required.add(it.asText()) }
        schema.get("allOf")?.forEach { sub ->
            sub.get("required")?.forEach { required.add(it.asText()) }
        }
        return required
    }

    private fun parseContext(contextBytes: ByteArray): Map<String, String> {
        val node = JacksonSupport.jsonMapper.readTree(contextBytes)
        val ctx = if (node.has("@context")) node.get("@context") else node
        val map = mutableMapOf<String, String>()
        collectContextObjects(ctx).forEach { obj ->
            obj.fields().forEach { (key, value) ->
                when {
                    key.startsWith("@") -> Unit
                    value.isTextual -> map[key] = value.asText()
                    value.isObject && value.has("@id") -> map[key] = value.get("@id").asText()
                }
            }
        }
        return map
    }

    private fun parseContextPrefixes(contextBytes: ByteArray): Map<String, String> {
        val node = JacksonSupport.jsonMapper.readTree(contextBytes)
        val ctx = if (node.has("@context")) node.get("@context") else node
        val prefixes = mutableMapOf<String, String>()
        collectContextObjects(ctx).forEach { obj ->
            obj.fields().forEach { (key, value) ->
                when {
                    key.startsWith("@") -> Unit
                    value.isObject && value.has("@id") && value.get("@prefix")?.asBoolean() == true -> {
                        prefixes[key] = value.get("@id").asText()
                    }
                    value.isTextual && looksLikeNamespace(value.asText()) -> {
                        prefixes[key] = value.asText()
                    }
                }
            }
        }
        return prefixes
    }

    private fun collectContextObjects(ctx: JsonNode?): List<JsonNode> {
        if (ctx == null) {
            return emptyList()
        }
        return when {
            ctx.isObject -> listOf(ctx)
            ctx.isArray -> ctx.filter { it.isObject }
            else -> emptyList()
        }
    }

    private fun looksLikeNamespace(value: String): Boolean {
        return value.endsWith("#") || value.endsWith("/") || value.endsWith(":")
    }

    private fun resolvePath(name: String, contextMap: Map<String, String>): String {
        return contextMap[name] ?: "${propertyNamespace}${sanitize(name)}"
    }

    private fun sanitize(raw: String): String {
        return raw.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    private fun enumLiteral(node: JsonNode): RdfTerm.Literal {
        return when {
            node.isTextual -> literal(node.asText())
            node.isBoolean -> booleanLiteral(node.asBoolean())
            node.isInt || node.isLong -> intLiteral(node.asLong().toInt())
            node.isNumber -> decimalLiteral(node.asText())
            else -> literal(node.toString())
        }
    }

    private fun iri(value: String) = RdfTerm.Iri(value)

    private fun bnode(): RdfTerm.BNode = RdfTerm.BNode("b${++bnodeCounter}")

    private fun literal(value: String) = RdfTerm.Literal(value)

    private fun booleanLiteral(value: Boolean) =
        RdfTerm.Literal(value.toString(), "${XSD}boolean")

    private fun intLiteral(value: Int) =
        RdfTerm.Literal(value.toString(), "${XSD}integer")

    private fun decimalLiteral(value: String) =
        RdfTerm.Literal(BigDecimal(value).toPlainString(), "${XSD}decimal")

    private fun numericLiteral(value: String): RdfTerm.Literal {
        val normalized = value.trim()
        return if (normalized.matches(Regex("^-?\\d+$"))) {
            intLiteral(normalized.toInt())
        } else {
            decimalLiteral(normalized)
        }
    }

    private class GraphBuilder(private val prefixes: Map<String, String>) {
        private val triples = mutableListOf<RdfTriple>()
        private var listCounter = 0

        fun add(subject: RdfTerm, predicate: RdfTerm.Iri, obj: RdfTerm) {
            triples += RdfTriple(subject, predicate, obj)
        }

        fun addType(subject: RdfTerm, typeIri: RdfTerm.Iri) {
            add(subject, RdfTerm.Iri("${RDF}type"), typeIri)
        }

        fun addList(values: List<RdfTerm>): RdfTerm {
            if (values.isEmpty()) {
                return RdfTerm.Iri("${RDF}nil")
            }
            val head = RdfTerm.BNode("l${++listCounter}")
            var current = head
            values.forEachIndexed { index, value ->
                add(current, RdfTerm.Iri("${RDF}first"), value)
                val next = if (index == values.lastIndex) {
                    RdfTerm.Iri("${RDF}nil")
                } else {
                    RdfTerm.BNode("l${++listCounter}")
                }
                add(current, RdfTerm.Iri("${RDF}rest"), next)
                current = next as? RdfTerm.BNode ?: return@forEachIndexed
            }
            return head
        }

        fun build(): RdfGraph = RdfGraph(prefixes = prefixes, triples = triples.toList())
    }

    private class RdfGraphTurtleWriter {
        fun write(graph: RdfGraph): String {
            val builder = StringBuilder()
            graph.prefixes.forEach { (prefix, uri) ->
                builder.appendLine("@prefix $prefix: <$uri> .")
            }
            builder.appendLine()
            graph.triples.forEach { triple ->
                builder.appendLine("${formatTerm(triple.subject)} ${formatTerm(triple.predicate)} ${formatTerm(triple.obj)} .")
            }
            return builder.toString()
        }

        private fun formatTerm(term: RdfTerm): String {
            return when (term) {
                is RdfTerm.Iri -> "<${term.value}>"
                is RdfTerm.BNode -> "_:${term.id}"
                is RdfTerm.Literal -> {
                    val escaped = term.value
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                    when {
                        term.language != null -> "\"$escaped\"@${term.language}"
                        term.datatypeIri != null -> "\"$escaped\"^^<${term.datatypeIri}>"
                        else -> "\"$escaped\""
                    }
                }
            }
        }
    }

    private companion object {
        const val SHACL = "http://www.w3.org/ns/shacl#"
        const val XSD = "http://www.w3.org/2001/XMLSchema#"
        const val RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"

        val SHACL_NODE_SHAPE = RdfTerm.Iri("${SHACL}NodeShape")
        val SHACL_PROPERTY_SHAPE = RdfTerm.Iri("${SHACL}PropertyShape")
        val SHACL_PROPERTY = RdfTerm.Iri("${SHACL}property")
        val SHACL_PATH = RdfTerm.Iri("${SHACL}path")
        val SHACL_MIN_COUNT = RdfTerm.Iri("${SHACL}minCount")
        val SHACL_MAX_COUNT = RdfTerm.Iri("${SHACL}maxCount")
        val SHACL_DATATYPE = RdfTerm.Iri("${SHACL}datatype")
        val SHACL_IN = RdfTerm.Iri("${SHACL}in")
        val SHACL_PATTERN = RdfTerm.Iri("${SHACL}pattern")
        val SHACL_MIN_LENGTH = RdfTerm.Iri("${SHACL}minLength")
        val SHACL_MAX_LENGTH = RdfTerm.Iri("${SHACL}maxLength")
        val SHACL_MIN_INCLUSIVE = RdfTerm.Iri("${SHACL}minInclusive")
        val SHACL_MAX_INCLUSIVE = RdfTerm.Iri("${SHACL}maxInclusive")
        val SHACL_MIN_EXCLUSIVE = RdfTerm.Iri("${SHACL}minExclusive")
        val SHACL_MAX_EXCLUSIVE = RdfTerm.Iri("${SHACL}maxExclusive")
        val SHACL_NODE = RdfTerm.Iri("${SHACL}node")
        val SHACL_TARGET_CLASS = RdfTerm.Iri("${SHACL}targetClass")
        val SHACL_LABEL = RdfTerm.Iri("${SHACL}label")
    }
}

