---
title: Ingest XML
layout: default
permalink: /how-to/ingest-xml/
---

Lift XML by converting it to JSON first, then applying a JSON-LD context.

## Prerequisites

- CLI installed
- An XML payload and context

## Steps

1. **Prepare XML input**

   `data.xml`

   ```xml
   <user id="user-1">
     <name>Ada Lovelace</name>
     <email>ada@example.com</email>
   </user>
   ```

   SemLift decodes this to JSON (attributes become `@`, text becomes `#text`):

   ```json
   {
     "@id": "user-1",
     "name": {
       "#text": "Ada Lovelace"
     },
     "email": {
       "#text": "ada@example.com"
     }
   }
   ```

2. **Shape the decoded JSON with a jq pre-step**

   `plan.yaml`

   ```yaml
   context:
     ref: "context.jsonld"
   additionalSteps:
     - type: jq
       code: |
         {
           id: .["@id"],
           type: "User",
           name: .name["#text"],
           email: .email["#text"]
         }
   ```

3. **Define the JSON-LD context**

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

4. **Run the lift**

   ```bash
   semantic-lift lift \
     --input data.xml \
     --inputFormat xml \
     --plan plan.yaml \
     --out out.ttl
   ```

## Expected output

RDF triples for the XML content.

## Performance note

XML decoding preserves element ordering. Use `jq` to prune unused fields before JSON-LD uplift.

