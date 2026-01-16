package io.semlift

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

class DefaultSemanticLifter(
    private val json: JsonCodec,
    private val resolver: ResourceResolver,
    private val jsonLd: JsonLdToRdf,
    private val rdf: RdfBackend,
    private val jq: JqRunner? = null,
    private val decoderRegistry: DecoderRegistry = DefaultDecoderRegistry(),
    private val jsonSchemaValidator: JsonSchemaValidator = JsonSchemaValidator()
) : SemanticLifter {
    override suspend fun lift(
        input: InputSource,
        plan: LiftPlan,
        options: LiftOptions
    ): LiftResult {
        val jsonDocument = decode(input, options)
        var elem = json.parse(jsonDocument)

        val applied = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        for (step in plan.pre) {
            elem = when (step) {
                is PreStep.KotlinJson -> step.transform.apply(elem)
                is PreStep.ExternalJq -> {
                    val runner = jq ?: error("jq step used but no JqRunner provided")
                    val out = runner.apply(step.program, json.encode(elem).bytes)
                    json.parse(JsonDocument(out))
                }
                is PreStep.JsonSchema -> {
                    val result = jsonSchemaValidator.validate(step.schema, json.encode(elem).bytes)
                    if (!result.valid) {
                        if (step.strict) {
                            error("JSON Schema validation failed: ${result.errors.joinToString("; ")}")
                        }
                        warnings += "JSON Schema validation failed: ${result.errors.joinToString("; ")}"
                    }
                    elem
                }
            }
            applied += step.name
        }

        val idRules = plan.idRules + options.idRulesOverride
        if (idRules.isNotEmpty()) {
            elem = applyIdRules(elem, idRules, options, warnings)
            applied += "id-rules"
        }

        val contextSpec = options.contextOverride ?: plan.context
        val contextBytes = when (contextSpec) {
            is ContextSpec.Inline -> contextSpec.jsonLd
            is ContextSpec.Resolved -> resolver.resolve(contextSpec.uri)
        }

        val jsonLdBytes = embedContext(json.encode(elem).bytes, contextBytes)
        var dataset = jsonLd.toDataset(jsonLdBytes, options.baseIri)

        var report: ValidationReport? = null
        for (step in plan.post) {
            when (step) {
                is PostStep.Shacl -> {
                    report = rdf.shaclValidate(dataset, step.shapesTurtle)
                    applied += step.name
                    if (options.strict && !report.conforms) {
                        return LiftResult(
                            rdf = rdf.serialize(dataset, options.output),
                            report = report,
                            diagnostics = Diagnostics(applied, warnings)
                        )
                    }
                }
                is PostStep.SparqlConstruct -> {
                    dataset = rdf.sparqlConstruct(dataset, step.query)
                    applied += step.name
                }
                is PostStep.SparqlUpdate -> {
                    dataset = rdf.sparqlUpdate(dataset, step.update)
                    applied += step.name
                }
            }
        }

        val out = rdf.serialize(dataset, options.output)
        return LiftResult(out, report, Diagnostics(applied, warnings))
    }

    private suspend fun decode(input: InputSource, options: LiftOptions): JsonDocument {
        val decoder = decoderRegistry.decoderFor(input, options)
        return decoder.decode(input)
    }

    private fun embedContext(jsonBytes: ByteArray, contextBytes: ByteArray): ByteArray {
        val payload = json.parse(JsonDocument(jsonBytes))
        val context = normalizeContext(json.parse(JsonDocument(contextBytes)))
        val merged = when (payload) {
            is JsonObject -> JsonObject(mapOf("@context" to context) + payload)
            is kotlinx.serialization.json.JsonArray -> JsonObject(
                mapOf("@context" to context, "@graph" to payload)
            )
            else -> JsonObject(mapOf("@context" to context, "@value" to payload))
        }
        return json.encode(merged).bytes
    }

    private fun normalizeContext(context: JsonElement): JsonElement {
        return if (context is JsonObject && context.containsKey("@context")) {
            context["@context"] ?: context
        } else {
            context
        }
    }

    private fun applyIdRules(
        input: JsonElement,
        rules: List<IdRule>,
        options: LiftOptions,
        warnings: MutableList<String>
    ): JsonElement {
        var current = input
        for (rule in rules) {
            val scopeElement = rule.scope?.let { getAt(current, it) } ?: current
            if (scopeElement == null) {
                val message = "idRules scope not found: ${rule.scope}"
                if (rule.strict || options.strict) {
                    error(message)
                }
                warnings += message
                continue
            }
            val minted = expandTemplate(rule.template, scopeElement, rule, options, warnings) ?: continue
            current = setAt(current, rule.path, kotlinx.serialization.json.JsonPrimitive(minted))
        }
        return current
    }

    private fun expandTemplate(
        template: String,
        scopeElement: JsonElement,
        rule: IdRule,
        options: LiftOptions,
        warnings: MutableList<String>
    ): String? {
        val scopeObject = scopeElement as? JsonObject
        if (scopeObject == null) {
            val message = "idRules scope is not an object for template ${rule.template}"
            if (rule.strict || options.strict) {
                error(message)
            }
            warnings += message
            return null
        }
        val regex = "\\{([^}]+)}".toRegex()
        var missing = false
        val expanded = regex.replace(template) { match ->
            val key = match.groupValues[1]
            val value = scopeObject[key]
            val text = value?.let { jsonElementToString(it) }
            if (text == null) {
                missing = true
                match.value
            } else {
                text
            }
        }
        if (missing) {
            val message = "idRules template variables missing for template ${rule.template}"
            if (rule.strict || options.strict) {
                error(message)
            }
            warnings += message
            return null
        }
        return expanded
    }

    private fun jsonElementToString(value: JsonElement): String? {
        return when (value) {
            is kotlinx.serialization.json.JsonNull -> null
            is kotlinx.serialization.json.JsonPrimitive -> value.content
            else -> value.toString()
        }
    }
}

