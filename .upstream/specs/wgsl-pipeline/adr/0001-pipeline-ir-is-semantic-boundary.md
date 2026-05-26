# ADR 0001: PipelineIR Is The Semantic Boundary

Status: Accepted

## Context

Kanvas needs one paint-pipeline contract that CPU and WebGPU can both consume.
The temptation is to let generated WGSL, handwritten WebGPU paths, or CPU
kernel shapes define behavior independently.

## Decision

Keep `KanvasPipelineIR` as the shared semantic boundary for paint operations:
shader, color filter, blend, color space, coverage, and store.

Backend plans may fuse or specialize the IR, but they must not introduce hidden
paint semantics that are absent from the IR or documented compatibility plan.

## Consequences

- CPU and GPU can be compared from the same semantic input.
- WGSL IR stays a concrete GPU module layer.
- Geometry/Coverage can hand off coverage without leaking backend path details
  into paint.
- Backend-specific optimizations need diagnostics when they refuse or fall
  back.
