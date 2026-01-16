package io.semlift.rdf4j

import io.semlift.RdfBackend
import io.semlift.RdfDataset
import io.semlift.RdfDocument
import io.semlift.RdfGraph
import io.semlift.RdfTerm
import io.semlift.RdfOutput
import io.semlift.ValidationReport
import org.eclipse.rdf4j.model.Model
import org.eclipse.rdf4j.model.impl.LinkedHashModel
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.SHACL
import org.eclipse.rdf4j.model.vocabulary.RDF4J
import org.eclipse.rdf4j.query.QueryLanguage
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.rio.RDFFormat
import org.eclipse.rdf4j.rio.Rio
import org.eclipse.rdf4j.sail.memory.MemoryStore
import org.eclipse.rdf4j.sail.shacl.ShaclSail
import org.eclipse.rdf4j.sail.shacl.ShaclSailValidationException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class Rdf4jBackend : RdfBackend {
    override fun serialize(dataset: RdfDataset, out: RdfOutput): RdfDocument {
        val model = dataset.asRdf4j()
        val output = ByteArrayOutputStream()
        Rio.write(model, output, out.toFormat())
        return RdfDocument(output.toByteArray())
    }

    override fun serializeGraph(graph: RdfGraph, out: RdfOutput): RdfDocument {
        val factory = SimpleValueFactory.getInstance()
        val model = LinkedHashModel()
        graph.prefixes.forEach { (prefix, uri) -> model.setNamespace(prefix, uri) }
        graph.triples.forEach { triple ->
            val subject = when (val s = triple.subject) {
                is RdfTerm.Iri -> factory.createIRI(s.value)
                is RdfTerm.BNode -> factory.createBNode(s.id)
                is RdfTerm.Literal -> error("Subject cannot be literal: $s")
            }
            val predicate = factory.createIRI(triple.predicate.value)
            val obj = when (val o = triple.obj) {
                is RdfTerm.Iri -> factory.createIRI(o.value)
                is RdfTerm.BNode -> factory.createBNode(o.id)
                is RdfTerm.Literal -> {
                    when {
                        o.language != null -> factory.createLiteral(o.value, o.language)
                        o.datatypeIri != null -> factory.createLiteral(o.value, factory.createIRI(o.datatypeIri))
                        else -> factory.createLiteral(o.value)
                    }
                }
            }
            model.add(subject, predicate, obj)
        }
        val output = ByteArrayOutputStream()
        Rio.write(model, output, out.toFormat())
        return RdfDocument(output.toByteArray())
    }

    override fun shaclValidate(dataset: RdfDataset, shapesTtl: ByteArray): ValidationReport {
        val model = dataset.asRdf4j()
        val repo = shaclRepository()
        repo.connection.use { conn ->
            conn.begin()
            val shapesModel = parseTurtle(shapesTtl)
            conn.add(shapesModel, RDF4J.SHACL_SHAPE_GRAPH)
            conn.add(model)
            return try {
                conn.commit()
                ValidationReport(true, RdfDocument(conformsReport()))
            } catch (ex: ShaclSailValidationException) {
                val report = ex.validationReportAsModel()
                ValidationReport(false, serializeReport(report))
            } finally {
                conn.close()
            }
        }
    }

    override fun sparqlConstruct(dataset: RdfDataset, query: String): RdfDataset {
        val repo = inMemoryRepository(dataset.asRdf4j())
        repo.connection.use { conn ->
            val q = conn.prepareGraphQuery(QueryLanguage.SPARQL, query)
            val result = q.evaluate()
            val out = LinkedHashModel()
            result.forEach { out.add(it) }
            return Rdf4jDataset(out)
        }
    }

    override fun sparqlUpdate(dataset: RdfDataset, update: String): RdfDataset {
        val repo = inMemoryRepository(dataset.asRdf4j())
        repo.connection.use { conn ->
            conn.prepareUpdate(QueryLanguage.SPARQL, update).execute()
            val out = LinkedHashModel()
            conn.getStatements(null, null, null, true).use { statements ->
                statements.forEachRemaining { out.add(it) }
            }
            return Rdf4jDataset(out)
        }
    }

    private fun parseTurtle(bytes: ByteArray): Model {
        return ByteArrayInputStream(bytes).use { Rio.parse(it, "", RDFFormat.TURTLE) }
    }

    private fun serializeReport(model: Model): RdfDocument {
        val output = ByteArrayOutputStream()
        Rio.write(model, output, RDFFormat.TURTLE)
        return RdfDocument(output.toByteArray())
    }

    private fun conformsReport(): ByteArray {
        val factory = SimpleValueFactory.getInstance()
        val report = factory.createBNode()
        val model = LinkedHashModel()
        model.add(report, RDF.TYPE, SHACL.VALIDATION_REPORT)
        model.add(report, SHACL.CONFORMS, factory.createLiteral(true))
        val output = ByteArrayOutputStream()
        Rio.write(model, output, RDFFormat.TURTLE)
        return output.toByteArray()
    }

    private fun shaclRepository(): Repository {
        val sail = ShaclSail(MemoryStore())
        return SailRepository(sail).apply { init() }
    }

    private fun inMemoryRepository(model: Model): Repository {
        val repo = SailRepository(MemoryStore())
        repo.init()
        repo.connection.use { it.add(model) }
        return repo
    }
}

private fun RdfDataset.asRdf4j(): Model {
    return (this as? Rdf4jDataset)?.model ?: error("RdfDataset is not a Rdf4jDataset")
}

private fun RdfOutput.toFormat(): RDFFormat {
    return when (this) {
        RdfOutput.Turtle -> RDFFormat.TURTLE
        RdfOutput.JsonLd -> RDFFormat.JSONLD
        RdfOutput.NTriples -> RDFFormat.NTRIPLES
    }
}

