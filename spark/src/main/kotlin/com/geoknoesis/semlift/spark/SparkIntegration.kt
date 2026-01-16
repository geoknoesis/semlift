package io.semlift.spark

import io.semlift.DecoderRegistry
import io.semlift.DefaultDecoderRegistry
import io.semlift.DefaultSemanticLifter
import io.semlift.InputSource
import io.semlift.JqRunner
import io.semlift.JsonCodec
import io.semlift.JsonLdToRdf
import io.semlift.LiftOptions
import io.semlift.ResourceResolver
import io.semlift.RdfBackend
import io.semlift.SemanticLifter
import io.semlift.SyntaxDecoder
import org.apache.spark.sql.SparkSession

class SparkDecoderRegistry(
    private val spark: SparkSession,
    private val delegate: DecoderRegistry = DefaultDecoderRegistry()
) : DecoderRegistry {
    override fun decoderFor(input: InputSource, options: LiftOptions): SyntaxDecoder {
        return when (input) {
            is InputSource.Spark -> SparkToJsonDecoder(spark, options.sparkMaxRows)
            else -> delegate.decoderFor(input, options)
        }
    }
}

class SparkSemanticLifter(
    spark: SparkSession,
    json: JsonCodec,
    resolver: ResourceResolver,
    jsonLd: JsonLdToRdf,
    rdf: RdfBackend,
    jq: JqRunner? = null
) : SemanticLifter {
    private val lifter = DefaultSemanticLifter(
        json = json,
        resolver = resolver,
        jsonLd = jsonLd,
        rdf = rdf,
        jq = jq,
        decoderRegistry = SparkDecoderRegistry(spark)
    )

    override suspend fun lift(input: InputSource, plan: io.semlift.LiftPlan, options: LiftOptions): io.semlift.LiftResult {
        return lifter.lift(input, plan, options)
    }
}

