package io.semlift.jena

import io.semlift.JsonLdToRdf
import io.semlift.RdfDataset
import org.apache.jena.query.DatasetFactory
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFParser
import java.io.ByteArrayInputStream

class JenaJsonLdToRdf : JsonLdToRdf {
    override fun toDataset(jsonLd: ByteArray, baseIri: String): RdfDataset {
        val dataset = DatasetFactory.create()
        RDFParser.create()
            .lang(Lang.JSONLD)
            .base(baseIri)
            .source(ByteArrayInputStream(jsonLd))
            .parse(dataset)
        return JenaDataset(dataset)
    }
}

