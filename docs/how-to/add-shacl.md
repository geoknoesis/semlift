---
title: Add SHACL Validation
layout: default
permalink: /how-to/add-shacl/
---

SHACL validates RDF graphs against shape constraints.

## Prerequisites

- A lift plan and SHACL shapes file

## Steps

1. **Create a shapes file**

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

2. **Add the SHACL step**

   `plan.yaml`

   ```yaml
   context:
     ref: "context.jsonld"
   additionalSteps:
     - type: shacl
       ref: "shapes.ttl"
   ```

3. **Run in strict mode**

   ```bash
   semantic-lift lift \
     --input data.json \
     --inputFormat json \
     --plan plan.yaml \
     --out out.ttl \
     --strict
   ```

## Expected output

- `out.ttl` contains the RDF output.
- `out.ttl.shacl.ttl` contains the SHACL report.

## Performance note

SHACL is the most expensive post-step. Keep shapes minimal and target only required classes.


