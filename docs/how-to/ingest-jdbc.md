---
title: Ingest JDBC
layout: default
permalink: /how-to/ingest-jdbc/
---

This guide lifts rows from a database table or query into RDF.

## Prerequisites

- JDBC driver available at runtime
- A lift plan with JSON-LD context

## Steps

1. **Create the lift plan**

   `plan.yaml`

   ```yaml
   context:
     ref: "context.jsonld"
   ```

2. **Run a table lift**

   ```bash
   semantic-lift lift \
     --inputFormat db \
     --dbUrl jdbc:postgresql://localhost:5432/mydb \
     --dbTable users \
     --dbUser user \
     --dbPassword pass \
     --plan plan.yaml \
     --out out.ttl
   ```

3. **Run a query lift (optional)**

   ```bash
   semantic-lift lift \
     --inputFormat db \
     --dbUrl jdbc:postgresql://localhost:5432/mydb \
     --dbQuery "SELECT id, name, email FROM users WHERE active = true" \
     --dbUser user \
     --dbPassword pass \
     --plan plan.yaml \
     --out out.ttl
   ```

## Expected output

`out.ttl` will contain RDF triples for each row.

## Performance note

Use targeted queries to reduce result sets. The decoder paginates using `jdbcFetchSize`.


