package io.semlift.ogc

import io.semlift.ContextSpec
import io.semlift.InputSource
import io.semlift.LiftOptions
import io.semlift.PreStep
import io.semlift.RdfOutput
import io.semlift.ResourceResolver
import io.semlift.jena.JenaSemanticLifter
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OgcBblocksProviderTest {
    @Test
    fun `builds lift plan from registry`() = runBlocking {
        val registryJson = """
            {
              "validationReportJson": "https://example.org/report.json",
              "bblocks": [
                {
                  "itemIdentifier": "ogc.geo.common.data_types.geojson",
                  "name": "GeoJSON",
                  "ldContext": "https://example.org/context.jsonld",
                  "schema": {
                    "application/json": "https://example.org/schema.json"
                  },
                  "documentation": {
                    "bblocks-viewer": {
                      "url": "https://example.org/docs"
                    }
                  }
                }
              ]
            }
        """.trimIndent().toByteArray()
        val schemaBytes = """{ "type": "object" }""".toByteArray()
        val contextBytes = """{ "@context": {} }""".toByteArray()
        val reportJson = """
            {
              "bblocks": {
                "ogc.geo.common.data_types.geojson": {
                  "items": [
                    {
                      "source": {
                        "url": "https://example.org/example.json",
                        "requireFail": false
                      }
                    }
                  ]
                }
              }
            }
        """.trimIndent().toByteArray()
        val exampleBytes = """{ "name": "ok" }""".toByteArray()

        val resolver = FakeResolver(
            mapOf(
                OgcBblocksProvider.DEFAULT_REGISTRY_URL to registryJson,
                "https://example.org/schema.json" to schemaBytes,
                "https://example.org/context.jsonld" to contextBytes,
                "https://example.org/report.json" to reportJson,
                "https://example.org/example.json" to exampleBytes
            )
        )
        val provider = OgcBblocksProvider(resolver = resolver)

        val plan = provider.resolve("ogc.geo.common.data_types.geojson")

        assertThat(plan.context).isEqualTo(ContextSpec.Resolved("https://example.org/context.jsonld"))
        assertThat(plan.pre).hasSize(1)
        val step = plan.pre.first() as PreStep.JsonSchema
        assertThat(step.schema).isEqualTo(schemaBytes)

        val summary = provider.validateExamples("ogc.geo.common.data_types.geojson")
        assertThat(summary.total).isEqualTo(1)
        assertThat(summary.failed).isEqualTo(0)

        val lifter = JenaSemanticLifter(resolver = resolver)
        val result = lifter.lift(
            InputSource.Json(exampleBytes),
            plan,
            LiftOptions(output = RdfOutput.JsonLd, baseIri = "urn:base:")
        )
        val model = ModelFactory.createDefaultModel()
        RDFParser.source(ByteArrayInputStream(result.rdf.bytes))
            .lang(Lang.JSONLD)
            .parse(model)
        val turtle = ByteArrayOutputStream()
        org.apache.jena.riot.RDFDataMgr.write(turtle, model, Lang.TURTLE)
        assertThat(turtle.toByteArray()).isNotEmpty
    }
}

private class FakeResolver(
    private val data: Map<String, ByteArray>
) : ResourceResolver {
    override suspend fun resolve(uri: String): ByteArray {
        return data[uri] ?: error("Missing data for $uri")
    }
}

