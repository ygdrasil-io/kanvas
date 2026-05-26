# ADR 0002: WGSL IR Is The GPU Module Layer

Status: Accepted

## Context

The Kotlin WGSL parser can expose an internal IR and generate WGSL source. That
IR is useful for deterministic module construction, but it is shaped like GPU
program text, not Skia rendering semantics.

## Decision

Use WGSL IR for concrete GPU module construction only. It may build structs,
bindings, helpers, functions, entry points, and expressions. It must not
replace `KanvasPipelineIR`.

## Consequences

- CPU execution remains free to compile `KanvasPipelineIR` into scalar or
  Java 25 Vector kernels.
- Generated WGSL can become deterministic and parser-valid without becoming a
  general shader compiler.
- Runtime effects remain registered descriptors rather than arbitrary SkSL or
  WGSL execution.
