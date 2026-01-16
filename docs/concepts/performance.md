---
title: Performance & Costs
layout: default
permalink: /concepts/performance/
---

SemLift is designed for predictable, staged processing. Each stage has a different cost profile.

## Stage costs

- **Decoding**: linear in input size.
- **`jq` pre-steps**: run as external processes; cost scales with input size and program complexity.
- **JSON-LD to RDF**: depends on context complexity and node count.
- **SHACL**: often the dominant cost for large graphs.
- **SPARQL**: cost depends on pattern selectivity and dataset size.

## Practical guidance

- Use targeted SPARQL patterns to avoid full graph scans.
- Prefer Kotlin transforms for high-throughput pipelines to avoid `jq` process overhead.
- For JDBC, use explicit queries and tune `jdbcFetchSize`.


