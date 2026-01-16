---
title: Ingest Spark
layout: default
permalink: /how-to/ingest-spark/
---

Spark ingestion is available through the optional `spark` module. It runs inside a Spark session and normalizes rows to JSON before semantic lifting.

## Setup

Add the Spark module and Spark runtime to your build:

```kotlin
dependencies {
    implementation(project(":spark"))
    implementation("org.apache.spark:spark-sql_2.12:3.5.1")
}
```

## Example

```kotlin
val spark = SparkSession.builder()
    .master("local[1]")
    .appName("semlift")
    .getOrCreate()

val lifter = SparkSemanticLifter(
    spark,
    json = KotlinxJsonCodec(),
    resolver = DefaultResourceResolver(),
    jsonLd = JenaJsonLdToRdf(),
    rdf = JenaBackend()
)
val plan = LiftPlan(
    context = ContextSpec.Resolved("classpath:/context.jsonld")
)

val result = lifter.lift(
    InputSource.Spark(table = "people"),
    plan,
    LiftOptions(sparkMaxRows = 10000)
)
```

## Notes

- The Spark decoder collects rows to the driver as JSON. Use `sparkMaxRows` to guard against large datasets.
- Spark input is SDK-only for now (not available in the CLI).

