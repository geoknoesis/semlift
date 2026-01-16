package io.semlift

data class RdfGraph(
    val prefixes: Map<String, String> = emptyMap(),
    val triples: List<RdfTriple> = emptyList()
)

data class RdfTriple(
    val subject: RdfTerm,
    val predicate: RdfTerm.Iri,
    val obj: RdfTerm
)

sealed interface RdfTerm {
    data class Iri(val value: String) : RdfTerm
    data class BNode(val id: String) : RdfTerm
    data class Literal(
        val value: String,
        val datatypeIri: String? = null,
        val language: String? = null
    ) : RdfTerm
}

