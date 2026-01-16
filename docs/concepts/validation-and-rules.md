---
title: Validation & Rules
layout: default
permalink: /concepts/validation-and-rules/
---

Post-steps operate on the RDF dataset created by JSON-LD uplift.

## SHACL validation

SHACL shapes validate graph structure and constraints. Use `--strict` to fail the lift on non-conformance.

```yaml
additionalSteps:
  - type: shacl
    ref: "shapes.ttl"
```

## SPARQL CONSTRUCT

CONSTRUCT rules allow deterministic graph enrichment:

```yaml
additionalSteps:
  - type: sparql-construct
    code: |
      PREFIX ex: <https://example.com/>
      CONSTRUCT { ?s ex:normalized true }
      WHERE { ?s ?p ?o }
```

## SPARQL UPDATE

UPDATE rules apply in-place dataset changes:

```yaml
additionalSteps:
  - type: sparql-update
    code: |
      PREFIX ex: <https://example.com/>
      INSERT { ?s ex:validated true }
      WHERE { ?s ?p ?o }
```

## Performance note

SHACL and SPARQL run on full datasets. For large graphs, isolate targeted shapes and rules to minimize cost.


