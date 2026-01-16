---
title: Quickstart
layout: default
permalink: /getting-started/quickstart/
---

This quickstart lifts a small JSON document to RDF and validates it with SHACL.

## 1. Create an input JSON file

`data.json`

```json
{
  "id": "user-1",
  "type": "User",
  "name": "Ada Lovelace",
  "email": "ada@example.com"
}
```

## 2. Create a JSON-LD context

`context.jsonld`

```json
{
  "@context": {
    "@vocab": "https://example.com/",
    "id": "@id",
    "type": "@type",
    "name": "name",
    "email": "email"
  }
}
```

## 3. Add a SHACL shape

`shapes.ttl`

```turtle
@prefix sh: <http://www.w3.org/ns/shacl#> .
@prefix ex: <https://example.com/> .

ex:UserShape a sh:NodeShape ;
  sh:targetClass ex:User ;
  sh:property [
    sh:path ex:email ;
    sh:minCount 1 ;
  ] .
```

## 4. Define a lift plan

`plan.yaml`

```yaml
context:
  ref: "context.jsonld"
additionalSteps:
  - type: shacl
    ref: "shapes.ttl"
```

## 5. Run the lift

```bash
cli/build/install/semantic-lift/bin/semantic-lift lift \
  --input data.json \
  --inputFormat json \
  --plan plan.yaml \
  --out out.ttl
```

If SHACL is enabled, the report will be written to `out.ttl.shacl.ttl`.

## 7. Use the Kotlin DSL (optional)

```kotlin
val plan = liftPlan {
    context("context.jsonld")
    pre(PreStep.KotlinJson(transform = jsonTransform {
        default("/status", "active")
    }))
}
```

## 6. Inspect the output

`out.ttl` will contain RDF triples for the JSON payload. If SHACL violations occur, set `--strict` to fail the lift.

