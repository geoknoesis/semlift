package io.semlift

data class LiftPlan(
    val context: ContextSpec,
    val pre: List<PreStep> = emptyList(),
    val post: List<PostStep> = emptyList(),
    val idRules: List<IdRule> = emptyList(),
    val input: InputSpec? = null,
    val imports: List<PlanImport> = emptyList(),
    val metadata: PlanMetadata? = null
)

data class IdRule(
    val path: String,
    val template: String,
    val scope: String? = null,
    val strict: Boolean = false
)

data class PlanImport(
    val provider: String? = null,
    val id: String? = null,
    val ref: String? = null,
    val profile: String? = null
)

data class PlanMetadata(
    val title: String? = null,
    val description: String? = null,
    val author: String? = null,
    val date: String? = null,
    val version: String? = null,
    val license: String? = null,
    val keywords: List<String> = emptyList(),
    val schema: String? = null,
    val profile: String? = null,
    val profilesOf: List<String> = emptyList()
)

sealed interface ContextSpec {
    data class Inline(val jsonLd: ByteArray) : ContextSpec
    data class Resolved(val uri: String) : ContextSpec
}

sealed interface PreStep {
    val name: String

    data class KotlinJson(
        override val name: String = "kjson",
        val transform: JsonTransform
    ) : PreStep

    data class ExternalJq(
        override val name: String = "jq",
        val program: String
    ) : PreStep

    data class JsonSchema(
        override val name: String = "json-schema",
        val schema: ByteArray,
        val strict: Boolean = true
    ) : PreStep
}

sealed interface PostStep {
    val name: String

    data class Shacl(
        override val name: String = "shacl",
        val shapesTurtle: ByteArray
    ) : PostStep

    data class SparqlConstruct(
        override val name: String = "sparql-construct",
        val query: String
    ) : PostStep

    data class SparqlUpdate(
        override val name: String = "sparql-update",
        val update: String
    ) : PostStep
}

sealed interface InputSpec {
    data class Api(
        val protocol: String,
        val config: ApiConfig
    ) : InputSpec
}


