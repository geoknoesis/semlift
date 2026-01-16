package io.semlift

import com.fasterxml.jackson.databind.JsonNode
import org.yaml.snakeyaml.Yaml
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class LiftPlanYamlLoader(
    private val resolver: ResourceResolver = DefaultResourceResolver(),
    private val planResolver: PlanResolver? = null
) {
    fun load(path: String): LiftPlan {
        val file = Paths.get(path)
        val bytes = Files.readAllBytes(file)
        return load(bytes, file.parent)
    }

    fun load(bytes: ByteArray, baseDir: Path? = null): LiftPlan {
        val yaml = Yaml()
        val root = yaml.load<Map<String, Any>>(String(bytes, StandardCharsets.UTF_8)) ?: emptyMap()
        val imports = parseImports(root["imports"], baseDir)
        val metadata = parseMetadata(root["metadata"])
        val contextResult = parseContextSpec(root["context"], baseDir)
        val context = contextResult.first
        val contextRules = contextResult.second
        val input = parseInput(root["input"], baseDir)
        val steps = parseSteps(root["additionalSteps"], baseDir)
        val planRules = parseIdRules(root["idRules"], baseDir)
        val idRules = contextRules + planRules
        if (imports.isEmpty()) {
            if (context == null) {
                error("Missing context in lift plan")
            }
            return LiftPlan(
                context = context,
                pre = steps.first,
                post = steps.second,
                idRules = idRules,
                input = input,
                imports = imports,
                metadata = metadata
            )
        }
        val resolvedImports = imports.map { importPlan(it, baseDir, mutableSetOf()) }
        val contexts = resolvedImports.map { it.context } + listOfNotNull(context)
        if (contexts.isEmpty()) {
            error("Missing context in lift plan")
        }
        val mergedContext = mergeContexts(contexts)
        val mergedPre = resolvedImports.flatMap { it.pre } + steps.first
        val mergedPost = resolvedImports.flatMap { it.post } + steps.second
        val mergedIdRules = resolvedImports.flatMap { it.idRules } + idRules
        val mergedInput = input ?: resolvedImports.firstOrNull { it.input != null }?.input
        val mergedMetadata = mergeMetadata(resolvedImports.mapNotNull { it.metadata }, metadata)
        return LiftPlan(
            context = mergedContext,
            pre = mergedPre,
            post = mergedPost,
            idRules = mergedIdRules,
            input = mergedInput,
            imports = imports,
            metadata = mergedMetadata
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseContextSpec(raw: Any?, baseDir: Path?): Pair<ContextSpec?, List<IdRule>> {
        val map = raw as? Map<String, Any> ?: return null to emptyList()
        val contextRules = parseIdRules(map["idRules"], baseDir)
        val ref = map["ref"] as? String
        if (ref != null) {
            return ContextSpec.Resolved(resolveRef(ref, baseDir)) to contextRules
        }
        val inline = map["inline"]
        if (inline != null) {
            val json = JacksonSupport.jsonMapper.writeValueAsBytes(inline)
            return ContextSpec.Inline(json) to contextRules
        }
        val json = map["json"]
        if (json != null) {
            return ContextSpec.Inline(JacksonSupport.jsonMapper.writeValueAsBytes(json)) to contextRules
        }
        error("Context must define ref or inline JSON")
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseSteps(raw: Any?, baseDir: Path?): Pair<List<PreStep>, List<PostStep>> {
        val items = raw as? List<Map<String, Any>> ?: emptyList()
        val pre = mutableListOf<PreStep>()
        val post = mutableListOf<PostStep>()
        items.forEach { step ->
            val type = step["type"]?.toString()?.lowercase() ?: error("Step missing type")
            val code = step["code"]?.toString()
            val ref = step["ref"]?.toString()
            val artifact = step["artifact"]?.toString()
            val className = step["class"]?.toString()
            when (type) {
                "jq" -> {
                    pre += PreStep.ExternalJq(program = code ?: resolveTextRef(ref, baseDir))
                }
                "kotlin-json", "kotlin" -> {
                    val transform = when {
                        ref != null -> loadTransform(ref)
                        artifact != null && className != null -> loadTransformFromArtifact(artifact, className, baseDir)
                        else -> error("kotlin-json step requires ref class name or artifact + class")
                    }
                    pre += PreStep.KotlinJson(transform = transform)
                }
                "json-schema" -> {
                    val strict = step["strict"]?.toString()?.toBoolean() ?: true
                    pre += PreStep.JsonSchema(schema = resolveBytes(code, ref, baseDir), strict = strict)
                }
                "shacl" -> {
                    post += PostStep.Shacl(shapesTurtle = resolveBytes(code, ref, baseDir))
                }
                "sparql-construct" -> {
                    post += PostStep.SparqlConstruct(query = code ?: resolveTextRef(ref, baseDir))
                }
                "sparql-update" -> {
                    post += PostStep.SparqlUpdate(update = code ?: resolveTextRef(ref, baseDir))
                }
                else -> error("Unsupported step type: $type")
            }
        }
        return pre to post
    }

    fun loadIdRules(path: String): List<IdRule> {
        val file = Paths.get(path)
        val bytes = Files.readAllBytes(file)
        return parseIdRulesFromBytes(bytes, file.parent)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseIdRules(raw: Any?, baseDir: Path?): List<IdRule> {
        return when (raw) {
            null -> emptyList()
            is List<*> -> raw.mapNotNull { parseIdRule(it) }
            is Map<*, *> -> {
                val ref = raw["ref"]?.toString()
                if (!ref.isNullOrBlank()) {
                    return parseIdRulesFromBytes(runBlockingResolve(ref, baseDir), baseDir)
                }
                val inline = raw["inline"] ?: raw["json"]
                if (inline != null) {
                    return parseIdRules(inline, baseDir)
                }
                if (raw.containsKey("idRules")) {
                    return parseIdRules(raw["idRules"], baseDir)
                }
                listOfNotNull(parseIdRule(raw))
            }
            else -> emptyList()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseIdRule(raw: Any?): IdRule? {
        val map = raw as? Map<*, *> ?: return null
        val path = map["path"]?.toString() ?: return null
        val template = map["template"]?.toString() ?: return null
        val scope = map["scope"]?.toString()
        val strict = map["strict"]?.toString()?.toBoolean() ?: false
        return IdRule(
            path = path,
            template = template,
            scope = scope,
            strict = strict
        )
    }

    private fun parseIdRulesFromBytes(bytes: ByteArray, baseDir: Path?): List<IdRule> {
        val yaml = Yaml()
        val parsed = yaml.load<Any>(String(bytes, StandardCharsets.UTF_8))
        return parseIdRules(parsed, baseDir)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseInput(raw: Any?, baseDir: Path?): InputSpec? {
        if (raw == null) {
            return null
        }
        val map = raw as? Map<String, Any> ?: error("Input must be a map")
        val type = map["type"]?.toString()?.lowercase() ?: error("Input requires type")
        return when (type) {
            "api" -> {
                val protocol = map["protocol"]?.toString()
                    ?: error("API input requires protocol")
                val rawConfig = map["config"] as? Map<String, Any?> ?: emptyMap()
                val config = parseApiConfig(protocol, rawConfig, baseDir)
                InputSpec.Api(protocol = protocol, config = config)
            }
            else -> error("Unsupported input type: $type")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseImports(raw: Any?, baseDir: Path?): List<PlanImport> {
        val items = raw as? List<Map<String, Any>> ?: return emptyList()
        return items.map { item ->
            val ref = item["ref"]?.toString()
            val provider = item["provider"]?.toString()
            val id = item["id"]?.toString()
            val profile = item["profile"]?.toString()
            if (ref == null && (provider == null || id == null)) {
                error("Import requires ref or provider+id")
            }
            val resolvedRef = if (ref != null && baseDir != null && isRelativeRef(ref)) {
                baseDir.resolve(ref).normalize().toString()
            } else {
                ref
            }
            PlanImport(provider = provider, id = id, ref = resolvedRef, profile = profile)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseMetadata(raw: Any?): PlanMetadata? {
        val map = raw as? Map<String, Any> ?: return null
        val keywords = (map["keywords"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
        val profilesOf = (map["profilesOf"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
        return PlanMetadata(
            title = map["title"]?.toString(),
            description = map["description"]?.toString(),
            author = map["author"]?.toString(),
            date = map["date"]?.toString(),
            version = map["version"]?.toString(),
            license = map["license"]?.toString(),
            keywords = keywords,
            schema = map["schema"]?.toString(),
            profile = map["profile"]?.toString(),
            profilesOf = profilesOf
        )
    }

    private fun mergeMetadata(imported: List<PlanMetadata>, local: PlanMetadata?): PlanMetadata? {
        if (local == null && imported.isEmpty()) {
            return null
        }
        val base = imported.firstOrNull()
        val mergedKeywords = if (!local?.keywords.isNullOrEmpty()) {
            local!!.keywords
        } else {
            imported.flatMap { it.keywords }.distinct()
        }
        val mergedProfilesOf = if (!local?.profilesOf.isNullOrEmpty()) {
            local!!.profilesOf
        } else {
            imported.flatMap { it.profilesOf }.distinct()
        }
        return PlanMetadata(
            title = local?.title ?: base?.title,
            description = local?.description ?: base?.description,
            author = local?.author ?: base?.author,
            date = local?.date ?: base?.date,
            version = local?.version ?: base?.version,
            license = local?.license ?: base?.license,
            keywords = mergedKeywords,
            schema = local?.schema ?: base?.schema,
            profile = local?.profile ?: base?.profile,
            profilesOf = mergedProfilesOf
        )
    }

    private fun mergeContexts(contexts: List<ContextSpec>): ContextSpec {
        if (contexts.isEmpty()) {
            error("No contexts to merge")
        }
        val contextNodes = contexts.map { contextElement(it) }
        val array = JacksonSupport.jsonMapper.createArrayNode()
        contextNodes.forEach { array.add(it) }
        val root = JacksonSupport.jsonMapper.createObjectNode()
        root.set<JsonNode>("@context", array)
        return ContextSpec.Inline(JacksonSupport.jsonMapper.writeValueAsBytes(root))
    }

    private fun contextElement(context: ContextSpec): JsonNode {
        return when (context) {
            is ContextSpec.Resolved -> JacksonSupport.jsonMapper.nodeFactory.textNode(context.uri)
            is ContextSpec.Inline -> {
                val node = JacksonSupport.jsonMapper.readTree(context.jsonLd)
                node.get("@context") ?: node
            }
        }
    }

    private fun importPlan(importRef: PlanImport, baseDir: Path?, visited: MutableSet<String>): LiftPlan {
        val key = when {
            importRef.ref != null -> "ref:${importRef.ref}"
            else -> "provider:${importRef.provider}:${importRef.id}:${importRef.profile ?: ""}"
        }
        if (!visited.add(key)) {
            error("Import cycle detected: $key")
        }
        val plan = when {
            importRef.ref != null -> loadImportRef(importRef.ref, baseDir)
            importRef.provider != null && importRef.id != null -> {
                val resolver = planResolver
                    ?: error("Plan import requires resolver for provider ${importRef.provider}")
                kotlinx.coroutines.runBlocking {
                    resolver.resolve(importRef.provider, importRef.id)
                }
            }
            else -> error("Import requires ref or provider+id")
        }
        if (plan.imports.isEmpty()) {
            return plan
        }
        val resolvedImports = plan.imports.map { importPlan(it, baseDir, visited) }
        val contexts = resolvedImports.map { it.context } + plan.context
        val mergedContext = mergeContexts(contexts)
        val mergedPre = resolvedImports.flatMap { it.pre } + plan.pre
        val mergedPost = resolvedImports.flatMap { it.post } + plan.post
        val mergedIdRules = resolvedImports.flatMap { it.idRules } + plan.idRules
        val mergedInput = plan.input ?: resolvedImports.firstOrNull { it.input != null }?.input
        val mergedMetadata = mergeMetadata(resolvedImports.mapNotNull { it.metadata }, plan.metadata)
        return plan.copy(
            context = mergedContext,
            pre = mergedPre,
            post = mergedPost,
            idRules = mergedIdRules,
            input = mergedInput,
            metadata = mergedMetadata
        )
    }

    private fun loadImportRef(ref: String, baseDir: Path?): LiftPlan {
        val resolved = resolveRef(ref, baseDir)
        return if (resolved.startsWith("http://") || resolved.startsWith("https://") || resolved.startsWith("classpath:")) {
            val bytes = runBlockingResolve(resolved, null)
            load(bytes, baseDir)
        } else {
            val path = Paths.get(resolved)
            val bytes = Files.readAllBytes(path)
            load(bytes, path.parent)
        }
    }

    private fun parseApiConfig(
        protocol: String,
        raw: Map<String, Any?>,
        baseDir: Path?
    ): ApiConfig {
        return when (protocol.lowercase()) {
            "ogc-api-features", "ogc", "ogc-api" -> {
                ApiConfig.OgcApiFeatures(
                    baseUrl = raw["baseUrl"]?.toString()
                        ?: error("OGC API config requires baseUrl"),
                    collection = raw["collection"]?.toString()
                        ?: error("OGC API config requires collection"),
                    limit = raw["limit"]?.toString()?.toIntOrNull(),
                    bbox = raw["bbox"]?.toString(),
                    params = parseStringMap(raw["params"]),
                    headers = parseStringMap(raw["headers"]),
                    recordPath = raw["recordPath"]?.toString()
                )
            }
            "wfs" -> {
                ApiConfig.Wfs(
                    baseUrl = raw["baseUrl"]?.toString()
                        ?: error("WFS config requires baseUrl"),
                    typeName = raw["typeName"]?.toString()
                        ?: error("WFS config requires typeName"),
                    version = raw["version"]?.toString() ?: "2.0.0",
                    outputFormat = raw["outputFormat"]?.toString() ?: "application/json",
                    srsName = raw["srsName"]?.toString(),
                    pageSize = raw["pageSize"]?.toString()?.toIntOrNull(),
                    startIndex = raw["startIndex"]?.toString()?.toIntOrNull(),
                    params = parseStringMap(raw["params"]),
                    headers = parseStringMap(raw["headers"]),
                    recordPath = raw["recordPath"]?.toString()
                )
            }
            "openapi", "open-api" -> {
                val specRef = raw["spec"]?.toString()
                    ?: error("OpenAPI config requires spec")
                val resolvedSpec = if (baseDir != null && isRelativeRef(specRef)) {
                    baseDir.resolve(specRef).normalize().toString()
                } else {
                    specRef
                }
                ApiConfig.OpenApi(
                    spec = resolvedSpec,
                    operationId = raw["operationId"]?.toString(),
                    path = raw["path"]?.toString(),
                    method = raw["method"]?.toString(),
                    server = raw["server"]?.toString(),
                    params = parseStringMap(raw["params"]),
                    headers = parseStringMap(raw["headers"]),
                    body = raw["body"]?.toString(),
                    recordPath = raw["recordPath"]?.toString()
                )
            }
            else -> ApiConfig.Custom(raw)
        }
    }

    private fun parseStringMap(raw: Any?): Map<String, String> {
        val map = raw as? Map<*, *> ?: return emptyMap()
        return map.entries
            .filter { it.key != null && it.value != null }
            .associate { it.key.toString() to it.value.toString() }
    }

    private fun isRelativeRef(ref: String): Boolean {
        return !(ref.startsWith("classpath:") ||
            ref.startsWith("http://") ||
            ref.startsWith("https://") ||
            ref.startsWith("file:") ||
            Paths.get(ref).isAbsolute)
    }

    private fun loadTransform(className: String): JsonTransform {
        val clazz = Class.forName(className)
        return when {
            JsonTransform::class.java.isAssignableFrom(clazz) -> {
                clazz.getDeclaredConstructor().newInstance() as JsonTransform
            }
            JsonTransformFactory::class.java.isAssignableFrom(clazz) -> {
                val factory = clazz.getDeclaredConstructor().newInstance() as JsonTransformFactory
                factory.create()
            }
            else -> error("Class $className must implement JsonTransform or JsonTransformFactory")
        }
    }

    private fun loadTransformFromArtifact(artifactRef: String, className: String, baseDir: Path?): JsonTransform {
        val resolved = resolveRef(artifactRef, baseDir)
        val bytes = runBlockingResolve(resolved, null)
        val file = kotlin.io.path.createTempFile("semlift-transform-", ".jar").toFile()
        file.deleteOnExit()
        file.writeBytes(bytes)
        val loader = java.net.URLClassLoader(arrayOf(file.toURI().toURL()), this::class.java.classLoader)
        val clazz = Class.forName(className, true, loader)
        return when {
            JsonTransform::class.java.isAssignableFrom(clazz) -> {
                clazz.getDeclaredConstructor().newInstance() as JsonTransform
            }
            JsonTransformFactory::class.java.isAssignableFrom(clazz) -> {
                val factory = clazz.getDeclaredConstructor().newInstance() as JsonTransformFactory
                factory.create()
            }
            else -> error("Class $className must implement JsonTransform or JsonTransformFactory")
        }
    }

    private fun resolveTextRef(ref: String?, baseDir: Path?): String {
        require(!ref.isNullOrBlank()) { "Step requires code or ref" }
        return runBlockingResolve(ref, baseDir).toString(StandardCharsets.UTF_8)
    }

    private fun resolveBytes(code: String?, ref: String?, baseDir: Path?): ByteArray {
        return when {
            code != null -> code.toByteArray(StandardCharsets.UTF_8)
            ref != null -> runBlockingResolve(ref, baseDir)
            else -> error("Step requires code or ref")
        }
    }

    private fun runBlockingResolve(ref: String, baseDir: Path?): ByteArray {
        val resolved = resolveRef(ref, baseDir)
        return kotlinx.coroutines.runBlocking {
            resolver.resolve(resolved)
        }
    }

    private fun resolveRef(ref: String, baseDir: Path?): String {
        if (ref.startsWith("classpath:") || ref.startsWith("http://") || ref.startsWith("https://") || ref.startsWith("file:")) {
            return ref
        }
        return if (baseDir != null) {
            baseDir.resolve(ref).normalize().toString()
        } else {
            ref
        }
    }
}

