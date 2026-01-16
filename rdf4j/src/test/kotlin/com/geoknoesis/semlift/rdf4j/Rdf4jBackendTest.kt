package io.semlift.rdf4j

import io.semlift.RdfOutput
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class Rdf4jBackendTest {
    @Test
    fun `jsonld to rdf and shacl validate`() {
        val jsonLd = """
            {
              "@context": {
                "id": "@id",
                "type": "@type",
                "@vocab": "http://example.org/",
                "name": "http://example.org/name"
              },
              "id": "urn:person:1",
              "type": "Person",
              "name": "Ada"
            }
        """.trimIndent().toByteArray()

        val shapes = """
            @prefix sh: <http://www.w3.org/ns/shacl#> .
            @prefix ex: <http://example.org/> .

            ex:PersonShape a sh:NodeShape ;
              sh:targetClass ex:Person ;
              sh:property [
                sh:path ex:name ;
                sh:minCount 1 ;
              ] .
        """.trimIndent().toByteArray()

        val jsonLdToRdf = Rdf4jJsonLdToRdf()
        val backend = Rdf4jBackend()
        val dataset = jsonLdToRdf.toDataset(jsonLd, "urn:base:")
        val rdf = backend.serialize(dataset, RdfOutput.NTriples)
        val report = backend.shaclValidate(dataset, shapes)

        assertThat(rdf.bytes.decodeToString()).contains("http://example.org/name")
        assertThat(report.conforms).isTrue
        assertThat(report.reportRdf.bytes).isNotEmpty
    }
}

