---
title: Contexts & Identifiers
layout: default
permalink: /concepts/contexts-and-ids/
---

The JSON-LD context is the core contract that drives SemLift’s semantics. It defines how JSON keys map to RDF predicates and how node identifiers are constructed.

## Context sources

Contexts can be defined inline or by reference:

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

## Identifier strategy

- Use stable identifiers (`@id`) whenever possible.
- Prefer URIs that encode domain identity rather than pipeline details.
- If you omit `@id`, JSON-LD will generate blank nodes.

## Identifier rules

Use `idRules` to mint identifiers from multiple fields before JSON-LD embedding:

```yaml
idRules:
  - path: /id
    template: "https://example.com/resource/{type}/{code}"
```

You can also inject rules via the CLI using `--idRules` to keep plans reusable.

## Base IRI

Use `--baseIri` to control how relative identifiers resolve.

```bash
semantic-lift lift --baseIri https://example.com/ ...
```

## Performance note

Stable identifiers improve deduplication and reduce graph churn in downstream stores.

