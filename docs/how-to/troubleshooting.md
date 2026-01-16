---
title: Troubleshooting
layout: default
permalink: /how-to/troubleshooting/
---

Common errors and their causes.

## Missing context in lift plan

Error:

```
Missing context in lift plan
```

Cause: `context` is required in `plan.yaml`.

Fix: Provide `context.ref`, `context.inline`, or `context.json`.

## Unsupported step type

Error:

```
Unsupported step type: <type>
```

Cause: Only `jq`, `shacl`, `sparql-construct`, and `sparql-update` are supported.

Fix: Update the step `type` or remove it.

## SHACL failed in strict mode

Error:

```
SHACL validation failed in strict mode.
```

Cause: A shape violation occurred and `--strict` is set.

Fix: Inspect the `*.shacl.ttl` report or rerun without `--strict` to debug.


