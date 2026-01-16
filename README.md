# SemLift

SemLift is a production-grade Kotlin/JVM platform for format-agnostic semantic lifting. It cleanly separates syntax decoding from semantic lifting, and produces governed RDF artifacts with JSON-LD, SHACL, and SPARQL.

## Architecture

```
[ Syntax Decoding Layer ] → [ Semantic Lifting Layer ] → RDF / SHACL / SPARQL
```

**Syntax decoding (format-specific)**
- JSON, XML, CSV, JDBC (PostgreSQL/MySQL/SQLite via JDBC)
- Output is always a normalized JSON document

**Semantic lifting (format-independent)**
- Optional JSON pre-steps (jq or Kotlin JSON DSL)
- Contextual uplift to JSON-LD
- JSON-LD parsed into RDF (Jena or RDF4J backends)
- Post-steps: SHACL validation, SPARQL CONSTRUCT, SPARQL UPDATE

## When to use this vs RML

SemLift is not an RML mapper. It is a semantic processing pipeline designed for deterministic lifting, validation, enrichment, and governance. Use it when you want:
- A single JSON-based semantic carrier across formats
- First-class SHACL validation reports
- Post-processing with SPARQL rules
- A library-first API with an operational CLI

## Supported Inputs

- JSON
- XML
- CSV
- JDBC (PostgreSQL, MySQL, SQLite)

### XML → JSON rules

- Attributes are prefixed with `@`
- Text nodes are represented as `#text`
- Element ordering is preserved

### CSV → JSON rules

- Output is an array of row objects
- Optional type coercion: int, decimal, boolean

### JDBC → JSON rules

- Query execution or `SELECT * FROM table`
- Array of row objects, column names as keys
- Fetch-size paging for large tables

## Lift Plan (YAML)

```yaml
context:
  ref: "classpath:/context.jsonld"
idRules:
  - path: /id
    template: "https://example.com/resource/{type}/{code}"
additionalSteps:
  - type: jq
    code: ".value |= tonumber"
  - type: kotlin-json
    ref: "com.example.transforms.MyTransform"
  - type: shacl
    ref: "shapes.ttl"
  - type: sparql-update
    code: |
      PREFIX ex: <http://example/>
      INSERT { ?s ex:validated true } WHERE { ?s ?p ?o }
```

### Kotlin JSON DSL (jq substitute)

Use `PreStep.KotlinJson` directly in code, or reference a `JsonTransform` class from YAML:

```kotlin
val plan = LiftPlan(
  context = ContextSpec.Resolved("classpath:/context.jsonld"),
  pre = listOf(
    PreStep.KotlinJson(
      transform = jsonTransform {
        set("/value", 42)
        default("/status", "active")
      }
    )
  )
)
```

The YAML `kotlin-json` step loads a class by name. The class must implement `JsonTransform`
or `JsonTransformFactory` and have a no-arg constructor.

## CLI

```
semantic-lift lift \
  --input data.csv \
  --inputFormat csv \
  --plan semantic-uplift.yaml \
  --out out.ttl
```

Options:
- `--context` overrides the plan context (path or URI)
- `--idRules` injects identifier rules (repeatable file paths)
- `--jqBinary` overrides the jq binary used for pre-steps
- `--strict` fails on SHACL non-conformance
- `--baseIri` sets JSON-LD base IRI
- `--rdfLang` controls RDF output format (turtle | jsonld | ntriples)

## Examples

### JSON

```
semantic-lift lift --input data.json --inputFormat json --plan plan.yaml --out out.ttl
```

### XML

```
semantic-lift lift --input data.xml --inputFormat xml --plan plan.yaml --out out.ttl
```

### CSV

```
semantic-lift lift --input data.csv --inputFormat csv --plan plan.yaml --out out.ttl
```

### JDBC (PostgreSQL)

```
semantic-lift lift \
  --inputFormat db \
  --dbUrl jdbc:postgresql://localhost:5432/mydb \
  --dbTable users \
  --dbUser user \
  --dbPassword pass \
  --plan plan.yaml \
  --out out.ttl
```

## Tests

Run:

```
./gradlew test
```

## Notes

- The core library is in `core`
- JSON/JSON-LD utilities are in `jsonld`
- Jena backend is in `jena`
- RDF4J backend is in `rdf4j`
- The CLI is in `cli`
- Provide JDBC drivers for PostgreSQL/MySQL at runtime

# semlift
