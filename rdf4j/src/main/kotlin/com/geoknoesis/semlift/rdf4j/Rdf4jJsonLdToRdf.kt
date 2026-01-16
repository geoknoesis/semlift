package io.semlift.rdf4j

import io.semlift.JsonLdToRdf
import io.semlift.RdfDataset
import org.eclipse.rdf4j.rio.RDFFormat
import org.eclipse.rdf4j.rio.Rio
import java.io.ByteArrayInputStream

class Rdf4jJsonLdToRdf : JsonLdToRdf {
    override fun toDataset(jsonLd: ByteArray, baseIri: String): RdfDataset {
        val model = ByteArrayInputStream(jsonLd).use { input ->
            Rio.parse(input, baseIri, RDFFormat.JSONLD)
        }
        return Rdf4jDataset(model)
    }
}

