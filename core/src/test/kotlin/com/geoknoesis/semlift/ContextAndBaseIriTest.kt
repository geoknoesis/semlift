package io.semlift

import io.semlift.jena.JenaSemanticLifter
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.ResourceFactory
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files

class ContextAndBaseIriTest {
    @Test
    fun `context resolver loads classpath and file refs`() {
        val resolver = DefaultResourceResolver()
        val classpathContext = kotlinx.coroutines.runBlocking {
            resolver.resolve("classpath:/samples/context.jsonld")
        }
        assertThat(classpathContext.isNotEmpty()).isTrue

        val temp = Files.createTempFile("context", ".jsonld")
        Files.writeString(temp, """{"@vocab":"http://example.org/"}""")
        val fileContext = kotlinx.coroutines.runBlocking {
            resolver.resolve(temp.toUri().toString())
        }
        assertThat(fileContext.decodeToString()).contains("http://example.org/")
    }

    @Test
    fun `base IRI is applied for relative identifiers`() = kotlinx.coroutines.runBlocking {
        val json = """{"id":"people/1","type":"Person","name":"Ada"}""".toByteArray()
        val plan = LiftPlan(
            context = ContextSpec.Resolved("classpath:/samples/context.jsonld")
        )

        val result = JenaSemanticLifter().lift(
            InputSource.Json(json),
            plan,
            LiftOptions(baseIri = "http://example.org/")
        )

        val model = ModelFactory.createDefaultModel()
        RDFDataMgr.read(model, result.rdf.bytes.inputStream(), Lang.TURTLE)

        val subject = ResourceFactory.createResource("http://example.org/people/1")
        assertThat(model.contains(subject, null)).isTrue
    }
}

