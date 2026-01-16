package io.semlift.rdf4j

import io.semlift.DefaultResourceResolver
import io.semlift.DefaultSemanticLifter
import io.semlift.JqProcessor
import io.semlift.ResourceResolver
import io.semlift.SemanticLifter
import io.semlift.jsonld.KotlinxJsonCodec

class Rdf4jSemanticLifter(
    resolver: ResourceResolver = DefaultResourceResolver(),
    private val jqBinary: String? = null
) : SemanticLifter {
    private val lifter = DefaultSemanticLifter(
        json = KotlinxJsonCodec(),
        resolver = resolver,
        jsonLd = Rdf4jJsonLdToRdf(),
        rdf = Rdf4jBackend(),
        jq = jqBinary?.let { JqProcessor(it) }
    )

    override suspend fun lift(input: io.semlift.InputSource, plan: io.semlift.LiftPlan, options: io.semlift.LiftOptions): io.semlift.LiftResult {
        val effective = if (jqBinary != null) options.copy(jqBinary = jqBinary) else options
        return lifter.lift(input, plan, effective)
    }
}

