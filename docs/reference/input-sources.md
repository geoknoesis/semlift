---
title: Input Sources
layout: default
permalink: /reference/input-sources/
---

SemLift supports four core input sources plus an optional Spark input. All inputs are normalized to JSON.

## JSON

- CLI: `--inputFormat json`
- SDK: `InputSource.Json(bytes)`

## XML

- CLI: `--inputFormat xml`
- SDK: `InputSource.Xml(bytes)`

Decoded JSON uses:

- Attributes prefixed with `@`
- Text nodes as `#text`

## CSV

- CLI: `--inputFormat csv`
- SDK: `InputSource.Csv(bytes, hasHeader = true)`

CSV rows become an array of objects.

## JDBC

- CLI: `--inputFormat db`
- SDK: `InputSource.Jdbc(jdbcUrl, table, user, password, query)`

JDBC results are an array of row objects using column labels as keys.

## Spark (optional module)

- SDK: `InputSource.Spark(table = "my_table")` or `InputSource.Spark(query = "SELECT ...")`

Spark input is provided by the `spark` module and is SDK-only (not in the CLI yet). It requires a `SparkSemanticLifter` with adapters wired in.

## API protocols

- CLI: `--inputFormat plan` with an `input` block in the lift plan
- SDK: `InputSource.Api(protocol, config)`

Supported protocols include OGC API Features (`ogc-api-features`), WFS (`wfs`), and OpenAPI (`openapi`).

