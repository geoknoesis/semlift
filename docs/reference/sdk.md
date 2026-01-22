---
title: SDK Reference
layout: default
permalink: /reference/sdk/
---

SemLift provides a minimal Kotlin API for programmatic lifting.

## Kotlin DSL

Use the Kotlin DSL to build a `LiftPlan` in code and pass it directly to a lifter.
The DSL is a convenience for authoring plans programmatically; the resulting plan
is equivalent to the YAML/JSON model.

```kotlin
val plan = liftPlan {
    context("context.jsonld")
    pre(PreStep.KotlinJson(transform = jsonTransform {
        default("/status", "active")
    }))
}

val lifter = JenaSemanticLifter()
val result = lifter.lift(
    input = InputSource.Json(dataBytes),
    plan = plan,
    options = LiftOptions()
)
```

## Core entry point

```kotlin
val lifter = JenaSemanticLifter()
val result = lifter.lift(input, plan, options)
```

You can also use RDF4J:

```kotlin
val lifter = Rdf4jSemanticLifter()
val result = lifter.lift(input, plan, options)
```

## Spark module (optional)

Spark support lives in the `spark` module and provides a Spark-aware lifter and decoder registry.

```kotlin
val spark = SparkSession.builder().master("local[1]").getOrCreate()
val lifter = SparkSemanticLifter(
    spark,
    json = KotlinxJsonCodec(),
    resolver = DefaultResourceResolver(),
    jsonLd = JenaJsonLdToRdf(),
    rdf = JenaBackend()
)
val result = lifter.lift(
    InputSource.Spark(table = "people"),
    plan,
    LiftOptions()
)
```

## Types

### `SemanticLifter`

```kotlin
interface SemanticLifter {
    suspend fun lift(
        input: InputSource,
        plan: LiftPlan,
        options: LiftOptions = LiftOptions()
    ): LiftResult
}
```

### `LiftPlan`

```kotlin
data class LiftPlan(
    val context: ContextSpec,
    val pre: List<PreStep> = emptyList(),
    val post: List<PostStep> = emptyList(),
    val input: InputSpec? = null,
    val imports: List<PlanImport> = emptyList(),
    val metadata: PlanMetadata? = null
)
```

### `PlanProvider`

```kotlin
interface PlanProvider {
    val id: String
    suspend fun resolve(identifier: String): LiftPlan
}
```

### `PlanRegistry`

```kotlin
class PlanRegistry(
    private val providers: List<PlanProvider>
) {
    suspend fun resolve(providerId: String, identifier: String): LiftPlan
}
```

### `PlanResolver`

```kotlin
interface PlanResolver {
    suspend fun resolve(providerId: String, identifier: String): LiftPlan
}
```

### `DefaultPlanRegistry`

Register in-memory plans or providers, and resolve by id.

```kotlin
val registry = DefaultPlanRegistry()
registry.registerPlan("example", plan)

val resolved = registry.resolve(DefaultPlanRegistry.LOCAL_PROVIDER_ID, "example")
```

### `CompositePlanRegistry`

```kotlin
val composite = CompositePlanRegistry(
    listOf(registryA, registryB)
)
val plan = composite.resolve("provider", "id")
```

### `ContextSpec`

- `ContextSpec.Inline(jsonLd: ByteArray)`
- `ContextSpec.Resolved(uri: String)`

### `PreStep`

- `PreStep.ExternalJq(program: String)`
- `PreStep.KotlinJson(transform: JsonTransform)`
- `PreStep.JsonSchema(schema: ByteArray, strict: Boolean = true)`

### `PostStep`

- `PostStep.Shacl(shapesTurtle: ByteArray)`
- `PostStep.SparqlConstruct(query: String)`
- `PostStep.SparqlUpdate(update: String)`

### `JsonSchemaToShacl`

Generate SHACL shapes from a JSON Schema, optionally using a JSON-LD context.

```kotlin
val generator = JsonSchemaToShacl(
    ShaclConfig(
        targetNamespace = "urn:semlift:shape#",
        propertyNamespace = "urn:semlift:prop#"
    )
)
val shapes = generator.generate(schemaBytes, contextBytes, JenaBackend())
```

### `InputSpec`

- `InputSpec.Api(protocol: String, config: ApiConfig)`

### `ApiConfig`
### `PlanImport`

```kotlin
data class PlanImport(
    val provider: String? = null,
    val id: String? = null,
    val ref: String? = null,
    val profile: String? = null
)
```

### `PlanMetadata`

```kotlin
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
```

### `RdfBackend`

```kotlin
interface RdfBackend {
    fun serialize(dataset: RdfDataset, out: RdfOutput): RdfDocument
    fun serializeGraph(graph: RdfGraph, out: RdfOutput): RdfDocument
    fun shaclValidate(dataset: RdfDataset, shapesTtl: ByteArray): ValidationReport
    fun sparqlConstruct(dataset: RdfDataset, query: String): RdfDataset
    fun sparqlUpdate(dataset: RdfDataset, update: String): RdfDataset
}
```

### `RdfGraph`

```kotlin
data class RdfGraph(
    val prefixes: Map<String, String> = emptyMap(),
    val triples: List<RdfTriple> = emptyList()
)
```


- `ApiConfig.OgcApiFeatures(...)`
- `ApiConfig.Wfs(...)`
- `ApiConfig.OpenApi(...)`

### `LiftOptions`

```kotlin
data class LiftOptions(
    val baseIri: String = "urn:base:",
    val output: RdfOutput = RdfOutput.Turtle,
    val strict: Boolean = true,
    val jqBinary: String = "jq",
    val contextOverride: ContextSpec? = null,
    val csvInferTypes: Boolean = true,
    val jdbcFetchSize: Int = 1000,
    val sparkMaxRows: Int? = null
)
```

### `RdfOutput`

- `RdfOutput.Turtle`
- `RdfOutput.JsonLd`
- `RdfOutput.NTriples`

### `LiftResult`

```kotlin
data class LiftResult(
    val rdf: RdfDocument,
    val report: ValidationReport? = null,
    val diagnostics: Diagnostics = Diagnostics()
)
```

### `ValidationReport`

```kotlin
data class ValidationReport(
    val conforms: Boolean,
    val reportRdf: RdfDocument
)
```

### `Diagnostics`

```kotlin
data class Diagnostics(
    val appliedSteps: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)
```

### `InputSource`

```kotlin
sealed interface InputSource {
    data class Json(val bytes: ByteArray) : InputSource
    data class Xml(val bytes: ByteArray) : InputSource
    data class Csv(val bytes: ByteArray, val hasHeader: Boolean = true) : InputSource
    data class Jdbc(
        val jdbcUrl: String,
        val table: String,
        val user: String? = null,
        val password: String? = null,
        val query: String? = null
    ) : InputSource
    data class Spark(
        val table: String? = null,
        val query: String? = null
    ) : InputSource
    data class Api(
        val protocol: String,
        val config: ApiConfig
    ) : InputSource
}
```
## Plan registry

Use the registry to resolve lift plans from provider plugins.

```kotlin
val registry = PlanRegistry(
    listOf(MyPlanProvider())
)
val plan = registry.resolve(
    providerId = "example",
    identifier = "some-id"
)
```

The optional `ogc-bblocks` plugin module provides an `OgcBblocksProvider` implementation.

It can also validate published examples from the registry report:

```kotlin
val provider = OgcBblocksProvider()
val summary = provider.validateExamples("ogc.geo.common.data_types.geojson")
```

To generate an HTML report similar to the official registry report, run:

```bash
./gradlew :plugins:ogc-bblocks:runOgcBblockValidator [outputDir]
```

## Caching remote artifacts

Use `CachingResourceResolver` to cache remote JSON-LD contexts and schemas locally.

```kotlin
val resolver = CachingResourceResolver(
    config = CacheConfig(
        directory = Paths.get(System.getProperty("user.home"), ".semlift", "cache"),
        ttl = Duration.ofHours(24)
    )
)
val loader = LiftPlanYamlLoader(resolver = resolver)
```

