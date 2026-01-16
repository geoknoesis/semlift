package io.semlift.spark

import io.semlift.InputSource
import io.semlift.jsonld.KotlinxJsonCodec
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.runBlocking
import org.apache.spark.sql.RowFactory
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types.DataTypes
import org.apache.spark.sql.types.StructType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SparkToJsonDecoderTest {
    @Test
    fun `decodes spark table to json array`() = runBlocking {
        val spark = SparkSession.builder()
            .appName("semlift-test")
            .master("local[1]")
            .getOrCreate()

        try {
            val schema = StructType()
                .add("id", DataTypes.IntegerType, false)
                .add("name", DataTypes.StringType, false)

            val rows = listOf(
                RowFactory.create(1, "Ada"),
                RowFactory.create(2, "Linus")
            )

            val df = spark.createDataFrame(rows, schema)
            df.createOrReplaceTempView("people")

            val decoder = SparkToJsonDecoder(spark, maxRows = null)
            val document = decoder.decode(InputSource.Spark(table = "people"))

            val codec = KotlinxJsonCodec()
            val actual = codec.parse(document)
            val expected = JsonArray(
                listOf(
                    JsonObject(mapOf("id" to JsonPrimitive(1), "name" to JsonPrimitive("Ada"))),
                    JsonObject(mapOf("id" to JsonPrimitive(2), "name" to JsonPrimitive("Linus")))
                )
            )
            assertThat(actual).isEqualTo(expected)
        } finally {
            spark.stop()
        }
    }
}

