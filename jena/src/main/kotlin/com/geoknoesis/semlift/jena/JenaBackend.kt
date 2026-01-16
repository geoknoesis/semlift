package io.semlift.jena

import io.semlift.RdfBackend
import io.semlift.RdfDataset
import io.semlift.RdfDocument
import io.semlift.RdfGraph
import io.semlift.RdfTerm
import io.semlift.RdfOutput
import io.semlift.ValidationReport
import org.apache.jena.query.Dataset
import org.apache.jena.query.DatasetFactory
import org.apache.jena.rdf.model.AnonId
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.query.QueryExecutionFactory
import org.apache.jena.query.QueryFactory
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RDFParser
import org.apache.jena.shacl.ShaclValidator
import org.apache.jena.update.UpdateAction
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class JenaBackend : RdfBackend {
    override fun serialize(dataset: RdfDataset, out: RdfOutput): RdfDocument {
        val jena = dataset.asJena()
        val output = ByteArrayOutputStream()
        if (out == RdfOutput.NTriples) {
            RDFDataMgr.write(output, jena.defaultModel, Lang.NTRIPLES)
        } else {
            RDFDataMgr.write(output, jena, out.toLang())
        }
        return RdfDocument(output.toByteArray())
    }

    override fun serializeGraph(graph: RdfGraph, out: RdfOutput): RdfDocument {
        val model = ModelFactory.createDefaultModel()
        graph.prefixes.forEach { (prefix, uri) -> model.setNsPrefix(prefix, uri) }
        graph.triples.forEach { triple ->
            val subject = when (val s = triple.subject) {
                is RdfTerm.Iri -> model.createResource(s.value)
                is RdfTerm.BNode -> model.createResource(AnonId.create(s.id))
                is RdfTerm.Literal -> error("Subject cannot be literal: $s")
            }
            val predicate = model.createProperty(triple.predicate.value)
            val obj = when (val o = triple.obj) {
                is RdfTerm.Iri -> model.createResource(o.value)
                is RdfTerm.BNode -> model.createResource(AnonId.create(o.id))
                is RdfTerm.Literal -> {
                    when {
                        o.language != null -> model.createLiteral(o.value, o.language)
                        o.datatypeIri != null -> model.createTypedLiteral(o.value, o.datatypeIri)
                        else -> model.createLiteral(o.value)
                    }
                }
            }
            model.add(subject, predicate, obj)
        }
        val output = ByteArrayOutputStream()
        if (out == RdfOutput.NTriples) {
            RDFDataMgr.write(output, model, Lang.NTRIPLES)
        } else {
            RDFDataMgr.write(output, model, out.toLang())
        }
        return RdfDocument(output.toByteArray())
    }

    override fun shaclValidate(dataset: RdfDataset, shapesTtl: ByteArray): ValidationReport {
        val jena = dataset.asJena()
        val shapesModel = ModelFactory.createDefaultModel()
        RDFParser.source(ByteArrayInputStream(shapesTtl))
            .lang(Lang.TURTLE)
            .parse(shapesModel)
        val report = ShaclValidator.get().validate(shapesModel.graph, jena.defaultModel.graph)
        val output = ByteArrayOutputStream()
        RDFDataMgr.write(output, report.model, Lang.TURTLE)
        return ValidationReport(report.conforms(), RdfDocument(output.toByteArray()))
    }

    override fun sparqlConstruct(dataset: RdfDataset, query: String): RdfDataset {
        val jena = dataset.asJena()
        val parsed = QueryFactory.create(query)
        QueryExecutionFactory.create(parsed, jena).use { execution ->
            val model = execution.execConstruct()
            val next = DatasetFactory.create()
            next.defaultModel = model
            return JenaDataset(next)
        }
    }

    override fun sparqlUpdate(dataset: RdfDataset, update: String): RdfDataset {
        val jena = dataset.asJena()
        UpdateAction.parseExecute(update, jena)
        return dataset
    }
}

private fun RdfDataset.asJena(): Dataset {
    return (this as? JenaDataset)?.dataset ?: error("RdfDataset is not a JenaDataset")
}

private fun RdfOutput.toLang(): Lang {
    return when (this) {
        RdfOutput.Turtle -> Lang.TURTLE
        RdfOutput.JsonLd -> Lang.JSONLD
        RdfOutput.NTriples -> Lang.NTRIPLES
    }
}

