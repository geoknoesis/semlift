---
title: Ingest OGC API Features
layout: default
permalink: /how-to/ingest-ogc-api/
---

This guide shows how to fetch OGC API Features data via the new API input and lift it to RDF.

## Prerequisites

- A lift plan with an `input` block.
- CLI installed (`semantic-lift`).

## Example plan

Use the sample plan in `docs/how-to/ogc-api-features-plan.yaml`:

```yaml
input:
  type: api
  protocol: ogc-api-features
  config:
    baseUrl: "https://demo.pygeoapi.io/master"
    collection: "lakes"
    limit: 5
    params:
      f: "json"
    headers:
      Accept: "application/geo+json, application/json"
context:
  inline:
    "@vocab": "https://example.com/ogc/"
    "type": "@type"
```

## Run the lift

```bash
semantic-lift lift \
  --inputFormat plan \
  --plan docs/how-to/ogc-api-features-plan.yaml \
  --out - \
  --rdfLang jsonld \
  --baseIri urn:base:
```

## Notes

- Some OGC servers return HTML unless `f=json` or `Accept` headers are set.
- JSON-LD output avoids dataset writer limitations for Turtle.

