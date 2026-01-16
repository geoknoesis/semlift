---
title: SHACL Generation
layout: default
permalink: /reference/shacl/
---

SemLift can generate SHACL shapes from JSON Schema, optionally using JSON-LD contexts for prefix injection and predicate IRIs.

## CLI usage

```bash
semantic-lift shacl --schema schema.json --out shapes.ttl
```

```bash
semantic-lift shacl \
  --schema schema.json \
  --context context.jsonld \
  --targetNamespace urn:semlift:shape# \
  --propertyNamespace urn:semlift:prop#
```

## SDK usage

```kotlin
val generator = JsonSchemaToShacl(
    ShaclConfig(
        targetNamespace = "urn:semlift:shape#",
        propertyNamespace = "urn:semlift:prop#"
    )
)
val shapes = generator.generate(schemaBytes, contextBytes, JenaBackend())
```

## Supported JSON Schema constraints

- `type`: `string`, `integer`, `number`, `boolean`, `object`, `array`
- `required`
- `enum`
- `pattern`
- `minLength` / `maxLength`
- `minimum` / `maximum`
- `exclusiveMinimum` / `exclusiveMaximum`
- `minItems` / `maxItems`
- `items` for arrays
- `allOf` properties/required (merged)

## Output details

- Prefixes are injected from JSON-LD contexts when they look like namespace declarations.
- The target namespace defaults to `urn:semlift:shape#`.
- The generator emits Turtle via the configured RDF backend.

## OGC building blocks validator

The OGC bblocks validator generates `*.schema.shacl.ttl` artifacts next to each example
when a JSON Schema is available.


