---
title: Run SPARQL Post-Processing
layout: default
permalink: /how-to/run-sparql/
---

SPARQL post-steps enrich or transform the RDF dataset after JSON-LD uplift.

## Prerequisites

- A lift plan with JSON-LD context
- SPARQL query or update text

## Steps

1. **Add a SPARQL CONSTRUCT rule**

   `plan.yaml`

   ```yaml
   context:
     ref: "context.jsonld"
   additionalSteps:
     - type: sparql-construct
       code: |
         PREFIX ex: <https://example.com/>
         CONSTRUCT { ?s ex:normalized true }
         WHERE { ?s ?p ?o }
   ```

2. **Run the lift**

   ```bash
   semantic-lift lift --input data.json --inputFormat json --plan plan.yaml --out out.ttl
   ```

3. **Use SPARQL UPDATE (optional)**

   ```yaml
   additionalSteps:
     - type: sparql-update
       code: |
         PREFIX ex: <https://example.com/>
         INSERT { ?s ex:validated true }
         WHERE { ?s ?p ?o }
   ```

## Expected output

RDF output includes changes produced by SPARQL.

## Performance note

SPARQL rules operate on the full dataset. Keep patterns selective and prefer CONSTRUCT for deterministic outputs.


