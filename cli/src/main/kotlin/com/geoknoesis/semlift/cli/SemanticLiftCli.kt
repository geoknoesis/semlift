package io.semlift.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.required
import io.semlift.ContextSpec
import io.semlift.InputSource
import io.semlift.JsonSchemaToShacl
import io.semlift.LiftOptions
import io.semlift.PlanProvider
import io.semlift.PlanRegistry
import io.semlift.LiftPlanYamlLoader
import io.semlift.RdfOutput
import io.semlift.ShaclConfig
import io.semlift.jena.JenaBackend
import io.semlift.jena.JenaSemanticLifter
import kotlinx.coroutines.runBlocking
import java.io.File

fun main(args: Array<String>) = SemanticLiftCli().main(args)

class SemanticLiftCli : CliktCommand(name = "semantic-lift", invokeWithoutSubcommand = true) {
    init {
        subcommands(LiftCommand(), ShaclCommand())
    }

    override fun run() = Unit
}

private class ShaclCommand : CliktCommand(name = "shacl") {
    private val schema by option("--schema", help = "JSON Schema file path or '-' for stdin").required()
    private val context by option("--context", help = "JSON-LD context file path")
    private val out by option("--out", help = "Output SHACL file path or '-' for stdout").default("-")
    private val targetNamespace by option("--targetNamespace", help = "Target namespace for shapes")
        .default("urn:semlift:shape#")
    private val propertyNamespace by option("--propertyNamespace", help = "Namespace for property IRIs")
    private val targetClass by option("--targetClass", help = "Target class IRI for the root shape")
    private val shapeName by option("--shapeName", help = "Root shape name override")
    private val noLabels by option("--noLabels", help = "Disable rdfs:label-like sh:label").flag(default = false)

    override fun run() {
        val schemaBytes = readInputBytes(schema)
        val contextBytes = context?.let { File(it).readBytes() }
        val generator = JsonSchemaToShacl(
            ShaclConfig(
                targetNamespace = targetNamespace,
                propertyNamespace = propertyNamespace,
                targetClass = targetClass,
                shapeName = shapeName,
                includeLabels = !noLabels
            )
        )
        val shacl = generator.generate(schemaBytes, contextBytes, JenaBackend())
        if (out == "-") {
            echo(shacl)
        } else {
            File(out).writeText(shacl)
        }
    }

    private fun readInputBytes(path: String): ByteArray {
        if (path == "-") {
            return System.`in`.readBytes()
        }
        return File(path).readBytes()
    }
}

private class LiftCommand : CliktCommand(name = "lift") {
    private val input by option("--input", help = "Input file path or '-' for stdin")
    private val inputFormat by option("--inputFormat", help = "json | xml | csv | db | plan")
        .required()
    private val plan by option("--plan", help = "Lift plan YAML file").required()
    private val out by option("--out", help = "Output RDF file path or '-' for stdout").default("-")

    private val dbUrl by option("--dbUrl")
    private val dbTable by option("--dbTable")
    private val dbUser by option("--dbUser")
    private val dbPassword by option("--dbPassword")
    private val dbQuery by option("--dbQuery")

    private val csvNoHeader by option("--csvNoHeader").flag(default = false)
    private val csvInferTypes by option("--csvInferTypes")
        .default("true")

    private val contextOverride by option("--context", help = "Override context ref (path/URI)")
    private val idRules by option("--idRules", help = "Identifier rules file path (repeatable)").multiple()
    private val jqBinary by option("--jqBinary", help = "Path to jq binary").default("jq")
    private val strict by option("--strict").flag(default = false)
    private val baseIri by option("--baseIri")
    private val rdfLang by option("--rdfLang", help = "turtle | jsonld | ntriples")
        .default("turtle")
    private val planProviders by option(
        "--planProvider",
        help = "Fully qualified class name for a PlanProvider (repeatable)"
    ).multiple()

    override fun run() = runBlocking {
        val providerInstances = planProviders.map { className ->
            val clazz = Class.forName(className)
            val instance = clazz.getDeclaredConstructor().newInstance()
            instance as? PlanProvider
                ?: error("Plan provider $className must implement PlanProvider")
        }
        val registry = if (providerInstances.isEmpty()) null else PlanRegistry(providerInstances)
        val loader = LiftPlanYamlLoader(planResolver = registry)
        val planSpec = loader.load(plan)
        val injectedIdRules = idRules.flatMap { loader.loadIdRules(it) }
        val lifter = JenaSemanticLifter(jqBinary = jqBinary)

        val inputSource = buildInputSource(planSpec)
        val options = LiftOptions(
            baseIri = baseIri ?: "urn:base:",
            jqBinary = jqBinary,
            strict = strict,
            contextOverride = contextOverride?.let { ContextSpec.Resolved(it) },
            idRulesOverride = injectedIdRules,
            output = parseOutput(rdfLang),
            csvInferTypes = csvInferTypes.toBoolean()
        )

        val result = lifter.lift(inputSource, planSpec, options)
        val outputBytes = result.rdf.bytes
        if (out == "-") {
            echo(outputBytes.decodeToString())
        } else {
            File(out).writeBytes(outputBytes)
        }

        result.report?.let { report ->
            val reportPath = if (out == "-") null else "${out}.shacl.ttl"
            if (reportPath != null) {
                File(reportPath).writeBytes(report.reportRdf.bytes)
            }
            if (options.strict && !report.conforms) {
                throw IllegalStateException("SHACL validation failed in strict mode.")
            }
        }

        Unit
    }

    private fun buildInputSource(planSpec: io.semlift.LiftPlan): InputSource {
        return when (inputFormat.lowercase()) {
            "json" -> InputSource.Json(readInputBytes())
            "xml" -> InputSource.Xml(readInputBytes())
            "csv" -> InputSource.Csv(readInputBytes(), hasHeader = !csvNoHeader)
            "db", "jdbc" -> {
                val url = requireNotNull(dbUrl) { "--dbUrl is required for db input" }
                val table = requireNotNull(dbTable) { "--dbTable is required for db input" }
                InputSource.Jdbc(
                    jdbcUrl = url,
                    table = table,
                    user = dbUser,
                    password = dbPassword,
                    query = dbQuery
                )
            }
            "plan" -> inputFromPlan(planSpec)
            else -> error("Unsupported inputFormat: $inputFormat")
        }
    }

    private fun inputFromPlan(planSpec: io.semlift.LiftPlan): InputSource {
        val input = planSpec.input ?: error("Lift plan input is required for inputFormat plan")
        return when (input) {
            is io.semlift.InputSpec.Api -> InputSource.Api(
                protocol = input.protocol,
                config = input.config
            )
        }
    }

    private fun readInputBytes(): ByteArray {
        if (input == null || input == "-") {
            return System.`in`.readBytes()
        }
        return File(input!!).readBytes()
    }

    private fun parseOutput(raw: String): RdfOutput {
        return when (raw.lowercase()) {
            "turtle", "ttl" -> RdfOutput.Turtle
            "jsonld" -> RdfOutput.JsonLd
            "ntriples", "nt" -> RdfOutput.NTriples
            else -> error("Unsupported rdfLang: $raw")
        }
    }
}

