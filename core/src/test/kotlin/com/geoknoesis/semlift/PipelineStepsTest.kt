package io.semlift

import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.ResourceFactory
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.vocabulary.RDF
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import io.semlift.jena.JenaSemanticLifter

class PipelineStepsTest {
    @Test
    fun `kotlin json pre-step is applied`() = kotlinx.coroutines.runBlocking {
        val json = resourceBytes("/samples/sample.json")
        val transform = jsonTransform {
            set("/status", "active")
        }
        val plan = LiftPlan(
            context = ContextSpec.Resolved("classpath:/samples/context.jsonld"),
            pre = listOf(PreStep.KotlinJson(transform = transform))
        )

        val result = JenaSemanticLifter(jqBinary = "jq").lift(InputSource.Json(json), plan, LiftOptions())
        val model = parseTurtle(result.rdf.bytes)

        val subject = ResourceFactory.createResource("urn:person:1")
        val predicate = ResourceFactory.createProperty("http://example.org/status")
        assertThat(model.contains(subject, predicate)).isTrue
    }

    @Test
    fun `jq pre-step is applied when jq is available`() = kotlinx.coroutines.runBlocking {
        assumeTrue(jqAvailable(), "jq not available on PATH")

        val json = """{"value":"1"}""".toByteArray()
        val plan = LiftPlan(
            context = ContextSpec.Inline("""{"@vocab":"http://example.org/"}""".toByteArray()),
            pre = listOf(PreStep.ExternalJq(program = ".value |= tonumber"))
        )

        val result = JenaSemanticLifter().lift(InputSource.Json(json), plan, LiftOptions())
        val model = parseTurtle(result.rdf.bytes)
        val predicate = ResourceFactory.createProperty("http://example.org/value")
        assertThat(model.listStatements(null, predicate, null as org.apache.jena.rdf.model.RDFNode?).toList()).isNotEmpty
    }

    @Test
    fun `sparql update post-step mutates graph`() = kotlinx.coroutines.runBlocking {
        val json = resourceBytes("/samples/sample.json")
        val plan = LiftPlan(
            context = ContextSpec.Resolved("classpath:/samples/context.jsonld"),
            post = listOf(
                PostStep.SparqlUpdate(
                    update = """
                    PREFIX ex: <http://example.org/>
                    INSERT { ?s ex:validated true } WHERE { ?s ?p ?o }
                    """.trimIndent()
                )
            )
        )

        val result = JenaSemanticLifter().lift(InputSource.Json(json), plan, LiftOptions())
        val model = parseTurtle(result.rdf.bytes)
        val subject = ResourceFactory.createResource("urn:person:1")
        val predicate = ResourceFactory.createProperty("http://example.org/validated")
        assertThat(model.contains(subject, predicate)).isTrue
    }

    @Test
    fun `sparql construct post-step replaces graph`() = kotlinx.coroutines.runBlocking {
        val json = resourceBytes("/samples/sample.json")
        val plan = LiftPlan(
            context = ContextSpec.Resolved("classpath:/samples/context.jsonld"),
            post = listOf(
                PostStep.SparqlConstruct(
                    query = """
                    PREFIX ex: <http://example.org/>
                    CONSTRUCT { ?s ex:name ?o } WHERE { ?s ex:name ?o }
                    """.trimIndent()
                )
            )
        )

        val result = JenaSemanticLifter().lift(InputSource.Json(json), plan, LiftOptions())
        val model = parseTurtle(result.rdf.bytes)
        val subject = ResourceFactory.createResource("urn:person:1")
        assertThat(model.contains(subject, RDF.type)).isFalse
        assertThat(model.contains(subject, ResourceFactory.createProperty("http://example.org/name"))).isTrue
    }

    @Test
    fun `strict mode fails on shacl nonconformance`() = kotlinx.coroutines.runBlocking {
        val json = """{"id":"urn:person:1","type":"Person"}""".toByteArray()
        val plan = LiftPlan(
            context = ContextSpec.Resolved("classpath:/samples/context.jsonld"),
            post = listOf(PostStep.Shacl(shapesTurtle = resourceBytes("/samples/shapes.ttl")))
        )

        assertThatThrownBy {
            kotlinx.coroutines.runBlocking {
                JenaSemanticLifter().lift(
                    InputSource.Json(json),
                    plan,
                    LiftOptions(strict = true)
                )
            }
        }.isInstanceOf(IllegalStateException::class.java)
    }

    private fun parseTurtle(bytes: ByteArray): Model {
        val model = ModelFactory.createDefaultModel()
        RDFDataMgr.read(model, bytes.inputStream(), Lang.TURTLE)
        return model
    }

    private fun resourceBytes(path: String): ByteArray {
        return requireNotNull(javaClass.getResourceAsStream(path)) {
            "Missing test resource: $path"
        }.use { it.readBytes() }
    }

    private fun jqAvailable(): Boolean {
        return try {
            val process = ProcessBuilder("jq", "--version").start()
            process.waitFor() == 0
        } catch (ex: Exception) {
            false
        }
    }
}

