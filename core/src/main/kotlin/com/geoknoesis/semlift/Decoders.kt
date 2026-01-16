package io.semlift

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.csv.CsvSchema
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.sql.DriverManager

class JsonDecoder : SyntaxDecoder {
    override suspend fun decode(source: InputSource): JsonDocument {
        val jsonSource = source as InputSource.Json
        return JsonDocument(jsonSource.bytes)
    }
}

class XmlToJsonDecoder(
    private val mapper: com.fasterxml.jackson.dataformat.xml.XmlMapper = JacksonSupport.xmlMapper
) : SyntaxDecoder {
    override suspend fun decode(source: InputSource): JsonDocument {
        val xmlSource = source as InputSource.Xml
        val tree = mapper.readTree(ByteArrayInputStream(xmlSource.bytes))
        return JsonDocument(JacksonSupport.jsonMapper.writeValueAsBytes(tree))
    }
}

class CsvToJsonDecoder(
    private val inferTypes: Boolean,
    private val mapper: com.fasterxml.jackson.dataformat.csv.CsvMapper = JacksonSupport.csvMapper
) : SyntaxDecoder {
    override suspend fun decode(source: InputSource): JsonDocument {
        val csvSource = source as InputSource.Csv
        val root = mapper.nodeFactory.arrayNode()
        if (csvSource.hasHeader) {
            val schema = CsvSchema.emptySchema().withHeader()
            val reader = mapper.readerFor(Map::class.java).with(schema)
            val rows = reader.readValues<Map<String, String>>(csvSource.bytes)
            rows.forEachRemaining { row ->
                root.add(coerceObject(row, inferTypes))
            }
        } else {
            val schema = CsvSchema.emptySchema()
            val reader = mapper.readerFor(Array<String>::class.java).with(schema)
            val rows = reader.readValues<Array<String>>(csvSource.bytes)
            val buffered = mutableListOf<Array<String>>()
            var columnCount = 0
            rows.forEachRemaining { row ->
                buffered += row
                if (row.size > columnCount) {
                    columnCount = row.size
                }
            }
            buffered.forEach { row ->
                root.add(buildObjectFromRow(row, columnCount))
            }
        }
        return JsonDocument(JacksonSupport.jsonMapper.writeValueAsBytes(root))
    }

    private fun buildObjectFromRow(row: Array<String>, columnCount: Int): ObjectNode {
        val node = mapper.nodeFactory.objectNode()
        for (i in 0 until columnCount) {
            val key = "col${i + 1}"
            val raw = row.getOrNull(i) ?: ""
            node.set<JsonNode>(key, coerceValue(raw, inferTypes))
        }
        return node
    }

    private fun coerceObject(row: Map<String, String>, infer: Boolean): ObjectNode {
        val node = mapper.nodeFactory.objectNode()
        row.forEach { (key, value) ->
            node.set<JsonNode>(key, coerceValue(value, infer))
        }
        return node
    }

    private fun coerceValue(value: String?, infer: Boolean): JsonNode {
        if (!infer) {
            return mapper.nodeFactory.textNode(value ?: "")
        }
        val trimmed = value?.trim() ?: ""
        if (trimmed.equals("true", ignoreCase = true) || trimmed.equals("false", ignoreCase = true)) {
            return mapper.nodeFactory.booleanNode(trimmed.toBoolean())
        }
        val intValue = trimmed.toLongOrNull()
        if (intValue != null) {
            return mapper.nodeFactory.numberNode(intValue)
        }
        val decimal = trimmed.toBigDecimalOrNull()
        if (decimal != null) {
            return mapper.nodeFactory.numberNode(decimal)
        }
        return mapper.nodeFactory.textNode(trimmed)
    }
}

class JdbcToJsonDecoder(
    private val fetchSize: Int,
    private val mapper: com.fasterxml.jackson.databind.ObjectMapper = JacksonSupport.jsonMapper
) : SyntaxDecoder {
    override suspend fun decode(source: InputSource): JsonDocument {
        val jdbcSource = source as InputSource.Jdbc
        val root = mapper.nodeFactory.arrayNode()
        DriverManager.getConnection(
            jdbcSource.jdbcUrl,
            jdbcSource.user,
            jdbcSource.password
        ).use { connection ->
            connection.prepareStatement(
                jdbcSource.query ?: "SELECT * FROM ${jdbcSource.table}",
                java.sql.ResultSet.TYPE_FORWARD_ONLY,
                java.sql.ResultSet.CONCUR_READ_ONLY
            ).use { statement ->
                statement.fetchSize = fetchSize
                statement.executeQuery().use { resultSet ->
                    val meta = resultSet.metaData
                    val columns = (1..meta.columnCount).map { meta.getColumnLabel(it) }
                    while (resultSet.next()) {
                        val row = mapper.nodeFactory.objectNode()
                        columns.forEachIndexed { index, name ->
                            val value = resultSet.getObject(index + 1)
                            row.set<JsonNode>(name, jdbcValue(value))
                        }
                        root.add(row)
                    }
                }
            }
        }
        return JsonDocument(mapper.writeValueAsBytes(root))
    }

    private fun jdbcValue(value: Any?): JsonNode {
        return when (value) {
            null -> mapper.nodeFactory.nullNode()
            is Boolean -> mapper.nodeFactory.booleanNode(value)
            is Int -> mapper.nodeFactory.numberNode(value)
            is Long -> mapper.nodeFactory.numberNode(value)
            is Short -> mapper.nodeFactory.numberNode(value.toInt())
            is Float -> mapper.nodeFactory.numberNode(value.toDouble())
            is Double -> mapper.nodeFactory.numberNode(value)
            is BigDecimal -> mapper.nodeFactory.numberNode(value)
            else -> mapper.nodeFactory.textNode(value.toString())
        }
    }
}

class ApiProtocolDecoder(
    private val registry: ApiProtocolRegistry = DefaultApiProtocolRegistry(),
    private val resolver: ResourceResolver = DefaultResourceResolver()
) : SyntaxDecoder {
    override suspend fun decode(source: InputSource): JsonDocument {
        val apiSource = source as InputSource.Api
        val protocol = registry.protocolFor(apiSource.protocol)
        return protocol.fetch(apiSource, resolver)
    }
}

