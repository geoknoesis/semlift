---
title: Plan Composition
layout: default
permalink: /reference/plan-composition/
---

SemLift supports composing plans via `imports` and enriching them with descriptive metadata.

## Imports

Imports are resolved first, then merged into the local plan. This allows nested plans and profile layering.

```yaml
imports:
  - ref: "base-plan.yaml"
  - provider: ogc-bblocks
    id: ogc.geo.json-fg.feature
```

### Import resolution rules

- `ref` imports load another plan file (local path, `http(s)://`, or `classpath:`).
- `provider` + `id` imports resolve via a `PlanResolver`.
- Cycles are rejected with an error.
- Imports can be nested; resolution is depth-first.

### Merge semantics

- `context`: all imported contexts are combined into a JSON-LD `@context` array. Local context is last.
- `pre` steps: concatenated in import order, then local steps appended.
- `post` steps: concatenated in import order, then local steps appended.
- `idRules`: concatenated in import order, then context-scoped rules, then local rules appended.
- `input`: local input wins; otherwise the first imported input is used.
- `metadata`: local fields override; lists are combined.

## Metadata

Metadata is descriptive and can be used for discovery, profiling, and documentation.

```yaml
metadata:
  title: "JSON-FG Feature"
  description: "Profile for JSON-FG Feature objects"
  author: "OGC Building Blocks"
  version: "1.0.0"
  date: "2025-01-15"
  license: "CC-BY-4.0"
  keywords:
    - json-fg
    - feature
  schema: "https://schemas.example.org/feature.json"
  profile: "https://schemas.example.org/profiles/core"
  profilesOf:
    - "https://schemas.example.org/profiles/base"
```

## Using provider imports in the CLI

Provider imports require a resolver. In the CLI this is supplied via `--planProvider`.

```bash
semantic-lift lift \
  --inputFormat json \
  --input data.json \
  --plan plan.yaml \
  --planProvider io.semlift.ogc.OgcBblocksProvider
```

