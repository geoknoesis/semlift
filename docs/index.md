---
title: SemLift Documentation
layout: default
permalink: /
---

SemLift is a Kotlin/JVM semantic lifting pipeline that transforms JSON, XML, CSV, JDBC, or Spark data into governed RDF artifacts. It separates syntax decoding from semantic lifting, making behavior predictable, composable, and performance-aware.

## Documentation pillars

- **Getting Started**: install, run a first lift, and see output in minutes.
- **Concepts**: build a precise mental model of the pipeline, JSON-LD, and RDF output.
- **How-To Guides**: task-focused recipes for ingestion, validation, and enrichment.
- **Reference**: authoritative specs for the CLI, lift plans, and options.

## Architecture in one glance

```mermaid
flowchart LR
  A[Input bytes] --> B[Syntax decoding]
  B --> C[Normalized JSON]
  C --> D[Pre-steps (jq / Kotlin)]
  D --> E[JSON-LD context]
  E --> F[RDF dataset (Jena or RDF4J)]
  F --> G[Post-steps: SHACL + SPARQL]
  G --> H[RDF output]
```

## When to use SemLift

- You need a deterministic, format-agnostic semantic uplift pipeline.
- You want JSON-LD, SHACL, and SPARQL in one cohesive flow.
- You need a library-first API with optional CLI usage.

## Typical scenarios

- **Data lake metadata**: lift JSON/CSV metadata into RDF for governance and catalog integration.
- **Operational databases**: uplift JDBC tables to RDF with SHACL enforcement.
- **Interoperability gateway**: normalize XML/CSV feeds to RDF and apply SPARQL enrichment.
- **Spark-native pipelines**: run uplift inside Spark for large datasets and consistent semantics.

## Start here

- `getting-started/`: setup and first successful lift.
- `concepts/pipeline/`: how the lifting pipeline works.
- `how-to/ingest-csv/`: common ingestion workflow.
- `reference/lift-plan/`: full lift plan schema.

