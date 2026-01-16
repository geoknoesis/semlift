---
title: Lift Plan YAML Reference
layout: default
permalink: /reference/lift-plan/
---

The lift plan defines the JSON-LD context and optional pre/post processing steps.

## Top-level schema

```yaml
input: <input-spec>   # optional
metadata: <metadata>  # optional
imports:              # optional
  - <import>
context: <context-spec>
idRules:              # optional
  - <id-rule>
additionalSteps:
  - <step>
```

## Context spec

Exactly one of `ref`, `inline`, or `json` must be provided.

```yaml
context:
  ref: "context.jsonld"
```

```yaml
context:
  inline:
    "@context":
      "@vocab": "https://example.com/"
      "id": "@id"
```

```yaml
context:
  json:
    "@context":
      "@vocab": "https://example.com/"
      "id": "@id"
```

Inline context array (multiple contexts):

```yaml
context:
  json:
    "@context":
      - "https://example.com/base.jsonld"
      - "@vocab": "https://example.com/vocab/"
        "id": "@id"
```

## Steps

Each step has a `type` and either `code` or `ref`.

### `jq` (pre-step)

Runs a `jq` program on the normalized JSON.

```yaml
additionalSteps:
  - type: jq
    code: ".value |= tonumber"
```

## Identifier rules

Identifier rules provide a declarative shortcut for minting identifiers before JSON-LD embedding.

```yaml
idRules:
  - path: /id
    template: "https://example.com/resource/{type}/{code}"
```

Rules can be referenced from an external file:

```yaml
idRules:
  ref: "id-rules.yaml"
```

Rules can also be declared inside the context block:

```yaml
context:
  ref: "context.jsonld"
  idRules:
    - path: /id
      template: "https://example.com/resource/{code}"
```

To keep plans portable, prefer `code` or `ref` with relative paths:

```yaml
additionalSteps:
  - type: jq
    ref: "transforms/normalize.jq"
```

Multiple jq steps:

```yaml
additionalSteps:
  - type: jq
    code: ".items[] |= (.value |= tonumber)"
  - type: jq
    code: ".items |= map(select(.value > 0))"
```

### Declarative jq subset (optional)

For portability, you can express common transformations declaratively using `jq-declarative`.

```yaml
additionalSteps:
  - type: jq-declarative
    strict: false
    ops:
      - op: set
        path: /status
        value: active
      - op: copy
        from: /id
        path: /identifier
      - op: move
        from: /props/name
        path: /name
      - op: delete
        path: /props
      - op: rename
        from: /height
        path: /elevation
      - op: cast
        path: /elevation
        to: number
      - op: map
        path: /items
        apply:
          op: set
          path: /type
          value: item
      - op: filter
        path: /items
        expr: ".value > 0"
```

### `kotlin-json` (pre-step)

Runs a Kotlin JSON transform by class name.

```yaml
additionalSteps:
  - type: kotlin-json
    ref: "com.example.transforms.MyTransform"
```

The class must implement `JsonTransform` or `JsonTransformFactory` and expose a no-arg constructor.

Portable transforms can be packaged as a JAR and referenced directly:

```yaml
additionalSteps:
  - type: kotlin-json
    artifact: "https://example.com/transforms/my-transform.jar"
    class: "com.example.transforms.MyTransform"
```

Factory-based transform:

```yaml
additionalSteps:
  - type: kotlin-json
    ref: "com.example.transforms.MyTransformFactory"
```

The loader will download the artifact, load the class, and instantiate it.

### `json-schema` (pre-step)

Validates the normalized JSON against a JSON Schema.

```yaml
additionalSteps:
  - type: json-schema
    ref: "schema.json"
    strict: true
```

Inline schema:

```yaml
additionalSteps:
  - type: json-schema
    code: |
      { "type": "object", "required": ["id"] }
```

### `shacl` (post-step)

Validates the RDF dataset using SHACL.

```yaml
additionalSteps:
  - type: shacl
    ref: "shapes.ttl"
```

Inline SHACL:

```yaml
additionalSteps:
  - type: shacl
    code: |
      @prefix sh: <http://www.w3.org/ns/shacl#> .
      @prefix ex: <https://example.com/> .
      ex:Shape a sh:NodeShape .
```

### `sparql-construct` (post-step)

Applies a SPARQL CONSTRUCT rule.

```yaml
additionalSteps:
  - type: sparql-construct
    code: |
      CONSTRUCT { ?s ?p ?o }
      WHERE { ?s ?p ?o }
```

Construct with filtering:

```yaml
additionalSteps:
  - type: sparql-construct
    code: |
      PREFIX ex: <https://example.com/>
      CONSTRUCT { ?s ex:status "active" }
      WHERE { ?s ex:enabled true }
```

### `sparql-update` (post-step)

Applies a SPARQL UPDATE statement.

```yaml
additionalSteps:
  - type: sparql-update
    code: |
      INSERT { ?s ?p ?o }
      WHERE { ?s ?p ?o }
```

Update with delete/insert:

```yaml
additionalSteps:
  - type: sparql-update
    code: |
      PREFIX ex: <https://example.com/>
      DELETE { ?s ex:status "old" }
      INSERT { ?s ex:status "new" }
      WHERE { ?s ex:status "old" }
```

## Imports (optional)

Use `imports` to compose plans explicitly. Imports are resolved first; the current plan overrides.

```yaml
imports:
  - ref: "base-plan.yaml"
```

```yaml
imports:
  - provider: ogc-bblocks
    id: ogc.geo.json-fg.feature
```

Multiple imports:

```yaml
imports:
  - ref: "base-plan.yaml"
  - provider: ogc-bblocks
    id: ogc.geo.json-fg.time
```

Import resolution rules:

- `ref` imports load another plan file (local path, `http(s)://`, or `classpath:`).
- `provider` + `id` imports resolve via a `PlanResolver`.
- Cycles are rejected with an error.
- Nested imports are resolved depth-first.

## Metadata (optional)

```yaml
metadata:
  title: "JSON-FG Feature"
  description: "Profile for JSON-FG Feature objects"
  author: "OGC Building Blocks"
  version: "1.0.0"
  date: "2025-01-15"
  license: "CC-BY-4.0"
  keywords:
    - json-fg
    - feature
  schema: "https://schemas.example.org/feature.json"
  profile: "https://schemas.example.org/profiles/core"
  profilesOf:
    - "https://schemas.example.org/profiles/base"
```

Minimal metadata:

```yaml
metadata:
  title: "Minimal Plan"
  version: "0.1.0"
```

## Ordering and semantics

- All `jq` steps are pre-steps and run in list order.
- SHACL and SPARQL steps are post-steps and run in list order.
- When `--strict` is set, SHACL failures abort the lift.
- When `imports` are used, imported contexts and steps are applied first.
- Imported contexts are merged into a JSON-LD `@context` array, with local context last.

## Input spec (optional)

The `input` block lets you define API inputs in the plan and use `--inputFormat plan` in the CLI.

```yaml
input:
  type: api
  protocol: ogc-api-features
  config:
    baseUrl: "https://example.com/ogc"
    collection: "buildings"
    limit: 100
    params:
      bbox: "-10,-10,10,10"
```

OGC API with headers and recordPath:

```yaml
input:
  type: api
  protocol: ogc-api-features
  config:
    baseUrl: "https://example.com/ogc"
    collection: "buildings"
    headers:
      Authorization: "Bearer token"
    recordPath: "features"
```

```yaml
input:
  type: api
  protocol: wfs
  config:
    baseUrl: "https://example.com/geoserver/wfs"
    typeName: "workspace:buildings"
    pageSize: 500
```

WFS with filters:

```yaml
input:
  type: api
  protocol: wfs
  config:
    baseUrl: "https://example.com/geoserver/wfs"
    typeName: "workspace:buildings"
    params:
      cql_filter: "height > 10"
```

```yaml
input:
  type: api
  protocol: openapi
  config:
    spec: "openapi.yaml"
    operationId: "listBuildings"
    params:
      limit: "1000"
    recordPath: "items"
```

## Non-API input examples

These formats are used via CLI flags and are shown here for reference.

JSON file:

```yaml
input:
  type: json
  path: "data.json"
```

XML file:

```yaml
input:
  type: xml
  path: "data.xml"
```

CSV file:

```yaml
input:
  type: csv
  path: "data.csv"
  hasHeader: true
```

JDBC source:

```yaml
input:
  type: jdbc
  jdbcUrl: "jdbc:postgresql://localhost:5432/demo"
  table: "buildings"
  user: "demo"
  password: "secret"
```

OpenAPI with explicit path/method:

```yaml
input:
  type: api
  protocol: openapi
  config:
    spec: "openapi.yaml"
    path: "/buildings"
    method: "get"
    params:
      limit: "50"
```

