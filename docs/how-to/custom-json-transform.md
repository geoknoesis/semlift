---
title: Custom JSON Transform (Kotlin)
layout: default
permalink: /how-to/custom-json-transform/
---

Use Kotlin transforms when you need logic that is hard to express in `jq`. Kotlin transforms are available through the SDK or via YAML using `kotlin-json` steps.

## Prerequisites

- The `core` module on your classpath

## Steps

1. **Define a transform**

   ```kotlin
   val transform = jsonTransform {
       set("/type", "User")
       default("/status", "active")
   }
   ```

2. **Build a lift plan programmatically**

   ```kotlin
   val plan = LiftPlan(
       context = ContextSpec.Resolved("context.jsonld"),
       pre = listOf(PreStep.KotlinJson(transform = transform)),
       post = emptyList()
   )
   ```

3. **Run the lift**

   ```kotlin
   val lifter = JenaSemanticLifter()
   val input = InputSource.Json(File("data.json").readBytes())
   val result = lifter.lift(input, plan)
   ```

## Expected output

The RDF output reflects the transformed JSON and the JSON-LD context.

## Performance note

Kotlin transforms run in-process and avoid spawning `jq`. Prefer them for large datasets or complex logic.

## DSL operations

- `set("/a/b", value)`
- `remove("/a/b")`
- `move("/from", "/to")`
- `default("/path", value)`
- `mapArray("/items") { set("/field", "value") }`

## YAML loading (optional)

You can also reference a Kotlin transform class in `semantic-uplift.yaml`:

```yaml
additionalSteps:
  - type: kotlin-json
    ref: "com.example.transforms.MyTransform"
```

The referenced class must implement `JsonTransform` or `JsonTransformFactory` and expose a no-arg constructor.

