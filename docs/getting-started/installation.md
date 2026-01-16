---
title: Installation
layout: default
permalink: /getting-started/installation/
---

SemLift ships as a Kotlin/JVM library with a CLI. Build the CLI from source using Gradle.

## Build the CLI

```bash
./gradlew :cli:installDist
```

The executable will be available at:

```
cli/build/install/semantic-lift/bin/semantic-lift
```

## Run the CLI via Gradle (no install)

```bash
./gradlew :cli:run --args="lift --inputFormat json --plan plan.yaml --input data.json --out out.ttl"
```

## Optional: JDBC drivers

SemLift expects JDBC drivers on the runtime classpath. Add PostgreSQL/MySQL drivers to your environment if you plan to lift from databases.


