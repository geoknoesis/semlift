---
title: Input Formats
layout: default
permalink: /concepts/input-formats/
---

SemLift normalizes all inputs into JSON before semantic lifting. This ensures consistent semantics regardless of source format.

## JSON

JSON is passed through directly.

## XML

XML is converted to JSON using these rules:

- Attributes are prefixed with `@`.
- Text nodes are represented as `#text`.
- Element ordering is preserved.

## CSV

CSV decoding produces an array of row objects:

- The first row is treated as a header by default.
- Type inference is optional (`--csvInferTypes`).

## JDBC

JDBC decoding produces an array of row objects:

- Use `--dbQuery` to supply a SQL query, or `--dbTable` for full table scans.
- Results are paged using `jdbcFetchSize` (default 1000).

## Spark (optional module)

Spark decoding runs inside a Spark session and returns JSON rows:

- Use `InputSource.Spark(table = "...")` or `InputSource.Spark(query = "...")`.
- Optional `sparkMaxRows` limits how many rows are collected to the driver.
- Spark is SDK-only and requires the `spark` module plus a configured lifter.

## API protocols

API protocols fetch and normalize data before lifting:

- OGC API Features, WFS, and OpenAPI are supported.
- Use `--inputFormat plan` in the CLI and define `input` in the lift plan.
- SDK: `InputSource.Api(protocol = "...", config = ...)`.

## Performance considerations

- Large JDBC tables benefit from explicit queries and pagination.
- CSV type inference adds parsing cost; disable it for raw throughput.
- Spark input scales better for large datasets, but collect limits should be used for large result sets.

