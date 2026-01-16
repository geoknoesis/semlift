---
title: Use Stdin/Stdout
layout: default
permalink: /how-to/stdin-stdout/
---

SemLift can read from stdin and write to stdout using `-`.

## Prerequisites

- A lift plan file

## Steps

1. **Pipe JSON into SemLift**

   ```bash
   cat data.json | semantic-lift lift \
     --input - \
     --inputFormat json \
     --plan plan.yaml \
     --out -
   ```

2. **Capture output to a file**

   ```bash
   cat data.json | semantic-lift lift \
     --input - \
     --inputFormat json \
     --plan plan.yaml \
     --out out.ttl
   ```

## Expected output

RDF is written to stdout or the provided output file.


