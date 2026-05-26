# ADR 0005: Runtime Effects Are Registered

Status: Accepted

## Context

Skia supports runtime effects through SkSL. Kanvas keeps API compatibility for
selected call sites but does not include a SkSL compiler or VM.

## Decision

Resolve `SkRuntimeEffect` through registered descriptors keyed by canonical
source. Each descriptor names supported uniforms, children, flags, CPU
implementation id, and optional WGSL implementation id.

Unregistered effects fail with an explicit diagnostic.

## Consequences

- Supported runtime effects are deterministic and testable.
- Missing effects produce actionable hashes/ids for future registration.
- Kanvas avoids silently pretending to support arbitrary SkSL.
- GPU support is added per registered WGSL implementation with parser evidence.
