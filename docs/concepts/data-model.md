---
title: Data Model (JSON-LD & RDF)
layout: default
permalink: /concepts/data-model/
---

SemLift uses **JSON-LD** as the bridge between raw JSON and RDF. The JSON-LD context declares the mapping between JSON keys and RDF predicates, identifiers, and types.

## JSON-LD essentials

- `@id` assigns a stable IRI to a node.
- `@type` assigns one or more RDF classes.
- `@context` defines predicate mappings and default vocabularies.

## Example mapping

Input JSON:

```json
{
  "id": "user-1",
  "type": "User",
  "name": "Ada Lovelace"
}
```

Context:

```json
{
  "@context": {
    "@vocab": "https://example.com/",
    "id": "@id",
    "type": "@type",
    "name": "name"
  }
}
```

Resulting RDF (Turtle):

```turtle
@prefix ex: <https://example.com/> .

ex:user-1 a ex:User ;
  ex:name "Ada Lovelace" .
```

## Predictable behavior

- JSON keys with no mapping are ignored by JSON-LD.
- Arrays map to multi-valued predicates.
- Objects become nested nodes when they define `@id` or `@type`.

