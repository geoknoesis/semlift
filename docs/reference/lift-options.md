---
title: Lift Options
layout: default
permalink: /reference/lift-options/
---

Lift options configure JSON-LD behavior, SHACL strictness, and decoding.

## CLI mapping

| Option | CLI flag | Description |
| --- | --- | --- |
| `baseIri` | `--baseIri` | Base IRI for JSON-LD |
| `output` | `--rdfLang` | Output RDF format |
| `jqBinary` | `--jqBinary` | Path to `jq` binary |
| `strict` | `--strict` | Fail on SHACL violations |
| `contextOverride` | `--context` | Override plan context |
| `idRulesOverride` | `--idRules` | Inject identifier rules |
| `csvInferTypes` | `--csvInferTypes` | Enable CSV type inference |
| `jdbcFetchSize` | _N/A_ | Fetch size for JDBC paging |
| `sparkMaxRows` | _N/A_ | Max rows to collect from Spark |

## SDK usage

```kotlin
val options = LiftOptions(
    baseIri = "https://example.com/",
    output = RdfOutput.Turtle,
    jqBinary = "jq",
    strict = true,
    idRulesOverride = emptyList(),
    csvInferTypes = true,
    jdbcFetchSize = 2000,
    sparkMaxRows = 10000
)
```

## Defaults

- `baseIri`: `urn:base:`
- `output`: `Turtle`
- `strict`: `true`
- `csvInferTypes`: `true`
- `jdbcFetchSize`: `1000`
- `sparkMaxRows`: `null` (no limit)

