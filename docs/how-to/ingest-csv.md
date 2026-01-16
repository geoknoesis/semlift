---
title: Ingest CSV
layout: default
permalink: /how-to/ingest-csv/
---

This guide lifts a CSV file into RDF using a JSON-LD context.

## Prerequisites

- CLI installed (`getting-started/installation/`)
- `data.csv`, `context.jsonld`, and `plan.yaml` available

## Steps

1. **Prepare a CSV file**

   `data.csv`

   ```csv
   id,name,email
   user-1,Ada Lovelace,ada@example.com
   ```

2. **Define the JSON-LD context**

   `context.jsonld`

   ```json
   {
     "@context": {
       "@vocab": "https://example.com/",
       "id": "@id",
       "name": "name",
       "email": "email"
     }
   }
   ```

3. **Create the lift plan**

   `plan.yaml`

   ```yaml
   context:
     ref: "context.jsonld"
   ```

4. **Run the lift**

   ```bash
   semantic-lift lift \
     --input data.csv \
     --inputFormat csv \
     --plan plan.yaml \
     --out out.ttl
   ```

## Expected output

`out.ttl` will contain RDF triples for each CSV row.

## Performance note

Disable type inference for maximum throughput:

```bash
semantic-lift lift --csvInferTypes false ...
```


