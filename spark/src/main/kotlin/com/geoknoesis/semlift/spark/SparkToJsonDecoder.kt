package io.semlift.spark

import io.semlift.InputSource
import io.semlift.JacksonSupport
import io.semlift.JsonDocument
import io.semlift.SyntaxDecoder
import org.apache.spark.sql.SparkSession

class SparkToJsonDecoder(
    private val spark: SparkSession,
    private val maxRows: Int?,
    private val mapper: com.fasterxml.jackson.databind.ObjectMapper = JacksonSupport.jsonMapper
) : SyntaxDecoder {
    override suspend fun decode(source: InputSource): JsonDocument {
        val sparkSource = source as InputSource.Spark
        val dataFrame = when {
            sparkSource.query != null -> spark.sql(sparkSource.query)
            sparkSource.table != null -> spark.table(sparkSource.table)
            else -> error("Spark input requires table or query.")
        }
        val limited = maxRows?.let { dataFrame.limit(it) } ?: dataFrame
        val array = mapper.nodeFactory.arrayNode()
        val iterator = limited.toJSON().toLocalIterator()
        while (iterator.hasNext()) {
            val json = iterator.next()
            array.add(mapper.readTree(json))
        }
        return JsonDocument(mapper.writeValueAsBytes(array))
    }
}

