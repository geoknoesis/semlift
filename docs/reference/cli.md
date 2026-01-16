---
title: CLI Reference
layout: default
permalink: /reference/cli/
---

The CLI exposes two commands: `semantic-lift lift` and `semantic-lift shacl`.

## Usage

```bash
semantic-lift lift --inputFormat <json|xml|csv|db|plan> --plan plan.yaml [options]
```

```bash
semantic-lift shacl --schema schema.json [options]
```


## Required flags

- `--inputFormat`: `json | xml | csv | db | plan`
- `--plan`: path to a lift plan YAML file

## Input flags

- `--input`: input file path (or `-` for stdin)
- `--out`: output file path (or `-` for stdout, default)
- `--planProvider`: fully qualified `PlanProvider` class name (repeatable)

When `--inputFormat plan` is used, the input is taken from the lift plan `input` block.

`--planProvider` is required if your plan uses `imports` with `provider`/`id` references.

Example:

```bash
semantic-lift lift \
  --inputFormat json \
  --input data.json \
  --plan plan.yaml \
  --planProvider io.semlift.ogc.OgcBblocksProvider
```

## CSV flags

- `--csvNoHeader`: treat the first row as data
- `--csvInferTypes`: `true | false` (default `true`)

## JDBC flags

- `--dbUrl`: JDBC URL
- `--dbTable`: table name
- `--dbQuery`: SQL query (optional)
- `--dbUser`: database user
- `--dbPassword`: database password

## Semantics flags

- `--context`: override the plan context (path or URI)
- `--idRules`: identifier rules file path (repeatable)
- `--jqBinary`: path to the `jq` binary (default `jq`)
- `--strict`: fail the lift if SHACL fails
- `--baseIri`: base IRI for JSON-LD processing
- `--rdfLang`: `turtle | jsonld | ntriples` (default `turtle`)

Note: `kotlin-json` steps can reference portable JARs via `artifact` + `class`.

## Output artifacts

- RDF output is written to `--out`
- SHACL report is written to `--out.shacl.ttl` if SHACL is used

## Spark note

Spark input is SDK-only and not currently exposed in the CLI.

## Building block validation

Building block validation is provided by the optional `ogc-bblocks` plugin module.

## SHACL generation

Generate SHACL shapes from a JSON Schema (optionally using a JSON-LD context).

### Required flags

- `--schema`: JSON Schema file path (or `-` for stdin)

### Optional flags

- `--context`: JSON-LD context file path
- `--out`: output SHACL file path (or `-` for stdout, default)
- `--targetNamespace`: namespace for shape IRIs (default `urn:semlift:shape#`)
- `--propertyNamespace`: namespace for property IRIs (defaults to `--targetNamespace`)
- `--targetClass`: target class IRI for the root shape
- `--shapeName`: root shape name override
- `--noLabels`: disable `sh:label` on shapes

